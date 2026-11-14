package com.flyer.launcher.core.widgets

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.util.SizeF
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.ceil

private const val TAG = "WidgetEngine"

@Immutable
data class WidgetProvider(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val packageName: String,
    val user: UserHandle,
    val userSerial: Long,
) {
    val key: String = "${info.provider.flattenToShortString()}@$userSerial"
}

/** Long-press aware host view so widgets participate in the launcher's long-press menus and drag. */
class FlyerWidgetHostView(context: Context) : AppWidgetHostView(context) {

    interface Listener {
        fun onLongPress(view: FlyerWidgetHostView, rawX: Float, rawY: Float)
        fun onDragMove(rawX: Float, rawY: Float)
        fun onDragEnd(rawX: Float, rawY: Float, cancelled: Boolean)
    }

    var listener: Listener? = null
    var longPressEnabled = true

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val timeout = ViewConfiguration.getLongPressTimeout().toLong()
    private var downX = 0f
    private var downY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var longPressed = false
    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        longPressed = true
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        listener?.onLongPress(this, lastRawX, lastRawY)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!longPressEnabled) return super.onInterceptTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressed = false
                downX = ev.x; downY = ev.y
                lastRawX = ev.rawX; lastRawY = ev.rawY
                handler.postDelayed(longPressRunnable, timeout)
            }
            MotionEvent.ACTION_MOVE -> {
                lastRawX = ev.rawX; lastRawY = ev.rawY
                if (!longPressed && (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop)) handler.removeCallbacks(longPressRunnable)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPressRunnable)
        }
        return longPressed
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!longPressed) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> listener?.onDragMove(ev.rawX, ev.rawY)
            MotionEvent.ACTION_UP -> { longPressed = false; listener?.onDragEnd(ev.rawX, ev.rawY, cancelled = false) }
            MotionEvent.ACTION_CANCEL -> { longPressed = false; listener?.onDragEnd(ev.rawX, ev.rawY, cancelled = true) }
        }
        return true
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        handler.removeCallbacks(longPressRunnable)
    }
}

private class FlyerAppWidgetHost(context: Context, id: Int) : AppWidgetHost(context, id) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        FlyerWidgetHostView(context)
}

/**
 * Widget Engine: owns the [AppWidgetHost], allocates/binds/deletes widget ids, creates host views
 * and keeps their size options in sync with the launcher grid.
 */
class WidgetEngine(private val context: Context) {

    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val host: AppWidgetHost = FlyerAppWidgetHost(context.applicationContext, HOST_ID)
    private val userManager = context.getSystemService(UserManager::class.java)
    private var listening = false

    /** Host views are cached by widget id so recomposition never recreates them. */
    private val views = HashMap<Int, FlyerWidgetHostView>()

    fun startListening() {
        if (listening) return
        runCatching { host.startListening() }.onSuccess { listening = true }.onFailure { Log.w(TAG, "startListening", it) }
    }

    fun stopListening() {
        if (!listening) return
        runCatching { host.stopListening() }
        listening = false
    }

    fun providers(): List<WidgetProvider> {
        val pm = context.packageManager
        val profiles = runCatching { userManager.userProfiles }.getOrDefault(emptyList())
        return profiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            runCatching { manager.getInstalledProvidersForProfile(user) }.getOrDefault(emptyList()).map { info ->
                val appLabel = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                }.getOrDefault(info.provider.packageName)
                WidgetProvider(info, info.loadLabel(pm) ?: appLabel, appLabel, info.provider.packageName, user, serial)
            }
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    fun findProvider(flat: String, userSerial: Long): WidgetProvider? {
        val cn = ComponentName.unflattenFromString(flat) ?: return null
        return providers().firstOrNull { it.info.provider == cn && it.userSerial == userSerial }
            ?: providers().firstOrNull { it.info.provider == cn }
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        if (id < 0) return
        views.remove(id)
        runCatching { host.deleteAppWidgetId(id) }
    }

    /** Returns true if bound without user interaction; otherwise the caller must launch [bindIntent]. */
    fun bindIfAllowed(id: Int, provider: WidgetProvider, options: Bundle? = null): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(id, provider.user, provider.info.provider, options) }.getOrDefault(false)

    fun bindIntent(id: Int, provider: WidgetProvider): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.user)

    fun needsConfiguration(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val features = info.widgetFeatures
            val optional = AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL
            if (features and optional != 0) return false
        }
        return true
    }

    fun isReconfigurable(info: AppWidgetProviderInfo): Boolean =
        info.configure != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0

    fun startConfigure(activity: Activity, id: Int, requestCode: Int): Boolean = try {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Configure activity failed", e)
        false
    }

    fun viewFor(activityContext: Context, id: Int): FlyerWidgetHostView? {
        views[id]?.let { return it }
        val info = providerInfo(id) ?: return null
        val view = runCatching { host.createView(activityContext, id, info) as FlyerWidgetHostView }
            .onFailure { Log.w(TAG, "createView($id)", it) }
            .getOrNull() ?: return null
        views[id] = view
        return view
    }

    /** Drops cached views (e.g. when the activity is recreated). */
    fun releaseViews() = views.clear()

    /** Tells the provider the size it is laid out at (dp), so it can pick the right layout. */
    fun updateSize(view: AppWidgetHostView, id: Int, widthDp: Float, heightDp: Float) {
        val options = runCatching { manager.getAppWidgetOptions(id) }.getOrNull() ?: Bundle()
        val w = widthDp.toInt()
        val h = heightDp.toInt()
        if (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) == w &&
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) == h
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp))) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { view.updateAppWidgetSize(null, w, h, w, h) }
        }
    }

    /** Minimum and default spans for a provider on a grid with the given cell size (dp). */
    fun spansFor(info: AppWidgetProviderInfo, cellWidthDp: Float, cellHeightDp: Float, columns: Int, rows: Int): SpanLimits {
        val dm = context.resources.displayMetrics
        fun pxToDp(px: Int) = px / dm.density
        fun spanFor(sizeDp: Float, cell: Float, max: Int) = ceil(sizeDp / cell.coerceAtLeast(1f)).toInt().coerceIn(1, max)
        val (defX, defY) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            info.targetCellWidth.coerceIn(1, columns) to info.targetCellHeight.coerceIn(1, rows)
        } else {
            spanFor(pxToDp(info.minWidth), cellWidthDp, columns) to spanFor(pxToDp(info.minHeight), cellHeightDp, rows)
        }
        val minW = if (info.minResizeWidth in 1 until info.minWidth) info.minResizeWidth else info.minWidth
        val minH = if (info.minResizeHeight in 1 until info.minHeight) info.minResizeHeight else info.minHeight
        var maxX = columns
        var maxY = rows
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (info.maxResizeWidth > 0) maxX = spanFor(pxToDp(info.maxResizeWidth), cellWidthDp, columns)
            if (info.maxResizeHeight > 0) maxY = spanFor(pxToDp(info.maxResizeHeight), cellHeightDp, rows)
        }
        val minX = spanFor(pxToDp(minW), cellWidthDp, columns).coerceAtMost(defX)
        val minY = spanFor(pxToDp(minH), cellHeightDp, rows).coerceAtMost(defY)
        val horizontal = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        return SpanLimits(
            defaultX = defX, defaultY = defY,
            minX = if (horizontal) minX else defX, minY = if (vertical) minY else defY,
            maxX = if (horizontal) maxOf(maxX, defX) else defX, maxY = if (vertical) maxOf(maxY, defY) else defY,
        )
    }

    data class SpanLimits(val defaultX: Int, val defaultY: Int, val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
        val resizable get() = minX != maxX || minY != maxY
    }

    companion object {
        const val HOST_ID = 0x464C59 // "FLY"
    }
}
