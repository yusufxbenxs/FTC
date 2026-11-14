package com.flyer.launcher.core.render

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.flyer.launcher.core.apps.AppDiscoveryEngine
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.apps.IconPackManager
import com.flyer.launcher.core.config.IconConfig
import com.flyer.launcher.core.config.IconShape
import com.flyer.launcher.core.model.IconSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Everything that influences the pixels of a rendered icon bitmap (part of the cache key). */
@Immutable
data class IconSpec(
    val sizePx: Int,
    val shape: IconShape,
    val cornerPercent: Int,
    val shapeLegacy: Boolean,
    val legacyBackground: Int,
    val themed: Boolean,
    val themedForeground: Int,
    val themedBackground: Int,
    val iconPack: String?,
    val insetPercent: Int,
    val badgeWork: Boolean,
) {
    val key: String =
        "$sizePx|$shape|$cornerPercent|$shapeLegacy|$legacyBackground|$themed|$themedForeground|$themedBackground|$iconPack|$insetPercent|$badgeWork"

    companion object {
        fun from(c: IconConfig, sizePx: Int) = IconSpec(
            sizePx = sizePx.coerceIn(16, 512),
            shape = c.shape,
            cornerPercent = c.cornerRadiusPercent,
            shapeLegacy = c.shapeLegacyIcons,
            legacyBackground = c.legacyBackground.argb,
            themed = c.themedIcons,
            themedForeground = c.themedForeground.argb,
            themedBackground = c.themedBackground.argb,
            iconPack = c.iconPack,
            insetPercent = c.insetPercent,
            badgeWork = c.badgeWorkApps,
        )
    }
}

/**
 * Rendering Engine for icons: resolves the right source drawable (override → icon pack → app),
 * renders it with the configured shape mask / theming and caches the result in a byte-bounded LRU.
 *
 * Rendering happens on a limited background dispatcher so scrolling never decodes on the UI thread.
 */
class IconEngine(
    private val context: Context,
    private val apps: AppDiscoveryEngine,
    val iconPacks: IconPackManager,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val density = context.resources.displayMetrics.densityDpi

    @OptIn(ExperimentalCoroutinesApi::class)
    private val renderDispatcher = Dispatchers.Default.limitedParallelism(2)

    private var cache = newCache(16)

    private fun newCache(mb: Int) = object : LruCache<String, ImageBitmap>(mb * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun setCacheSizeMb(mb: Int) {
        if (cache.maxSize() != mb * 1024 * 1024) cache.resize(mb * 1024 * 1024)
    }

    fun clear() = cache.evictAll()

    fun trim(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) cache.trimToSize(cache.maxSize() / 4)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE) cache.evictAll()
    }

    fun cacheKey(appKey: String, spec: IconSpec, source: IconSource?) = "$appKey#${spec.key}#${source?.hashCode() ?: 0}"

    fun peek(key: String): ImageBitmap? = cache.get(key)

    suspend fun appIcon(entry: AppEntry, spec: IconSpec, source: IconSource?): ImageBitmap {
        val key = cacheKey(entry.key, spec, source)
        cache.get(key)?.let { return it }
        return withContext(renderDispatcher) {
            cache.get(key) ?: renderApp(entry, spec, source).asImageBitmap().also { cache.put(key, it) }
        }
    }

    suspend fun shortcutIcon(info: ShortcutInfo?, cacheId: String, spec: IconSpec): ImageBitmap? {
        val key = "sc:$cacheId#${spec.key}"
        cache.get(key)?.let { return it }
        return withContext(renderDispatcher) {
            val drawable = info?.let { runCatching { launcherApps.getShortcutIconDrawable(it, density) }.getOrNull() }
                ?: return@withContext null
            render(drawable, spec.copy(iconPack = null), isPackIcon = false).asImageBitmap().also { cache.put(key, it) }
        }
    }

    /** Renders an arbitrary drawable with the icon pipeline (used for widget previews / pickers). */
    suspend fun renderDrawable(drawable: Drawable, spec: IconSpec): ImageBitmap = withContext(renderDispatcher) {
        render(drawable, spec, isPackIcon = false).asImageBitmap()
    }

    /** Icon-pack drawable thumbnails for the icon picker (unshaped). */
    suspend fun packDrawable(packPackage: String, name: String, sizePx: Int): ImageBitmap? = withContext(renderDispatcher) {
        val key = "pack:$packPackage:$name:$sizePx"
        cache.get(key) ?: iconPacks.load(packPackage)?.drawable(name, density)?.let { d ->
            drawToBitmap(d, sizePx).asImageBitmap().also { cache.put(key, it) }
        }
    }

    // ───────────── Rendering ─────────────

    private fun renderApp(entry: AppEntry, spec: IconSpec, source: IconSource?): Bitmap {
        // 1) explicit per-app override
        when (source) {
            is IconSource.Image -> loadFileBitmap(source.path, spec.sizePx)?.let { bmp ->
                return render(BitmapDrawable(context.resources, bmp), spec, isPackIcon = false)
            }
            is IconSource.Pack -> iconPacks.load(source.packPackage)?.drawable(source.drawableName, density)?.let {
                return badge(render(it, spec, isPackIcon = true), entry, spec)
            }
            is IconSource.OtherApp -> apps.resolve(source.appKey)?.takeIf { it.key != entry.key }?.let { other ->
                return badge(render(baseDrawable(other), spec, isPackIcon = false), entry, spec)
            }
            null -> Unit
        }
        // 2) icon pack mapping
        val pack = spec.iconPack?.let { iconPacks.load(it) }
        val component = entry.component
        if (pack != null && component != null) {
            val name = pack.drawableNameFor(component)
            val packDrawable = name?.let { pack.drawable(it, density) }
            if (packDrawable != null) return badge(render(packDrawable, spec, isPackIcon = true), entry, spec)
            if (pack.backs.isNotEmpty()) return badge(renderWithPackMask(baseDrawable(entry), pack, spec), entry, spec)
        }
        // 3) the app's own icon
        return badge(render(baseDrawable(entry), spec, isPackIcon = false), entry, spec)
    }

    private fun baseDrawable(entry: AppEntry): Drawable {
        entry.builtIn?.let { b -> ContextCompat.getDrawable(context, b.icon)?.let { return it } }
        val component = entry.component
        val user = entry.user ?: Process.myUserHandle()
        if (component != null) {
            val info = runCatching { launcherApps.resolveActivity(android.content.Intent().setComponent(component), user) }.getOrNull()
            info?.getIcon(density)?.let { return it }
        }
        return context.packageManager.defaultActivityIcon
    }

    private fun badge(bitmap: Bitmap, entry: AppEntry, spec: IconSpec): Bitmap {
        if (!spec.badgeWork || !entry.isWorkProfile || entry.user == null) return bitmap
        val badged = context.packageManager.getUserBadgedIcon(BitmapDrawable(context.resources, bitmap), entry.user)
        return drawToBitmap(badged, spec.sizePx)
    }

    private fun newPaint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun render(drawable: Drawable, spec: IconSpec, isPackIcon: Boolean): Bitmap {
        val size = spec.sizePx
        // Icon-pack icons are already designed with their own silhouette; keep them as-is.
        if (isPackIcon || spec.shape == IconShape.SYSTEM) {
            if (!isPackIcon && spec.themed && Build.VERSION.SDK_INT >= 33 && drawable is AdaptiveIconDrawable && drawable.monochrome != null) {
                return themedSystem(drawable, spec)
            }
            return drawToBitmap(drawable, size, inset = spec.insetPercent)
        }
        val shapePath = IconShapes.path(spec.shape, size.toFloat(), size.toFloat(), spec.cornerPercent)
        val maskPaint = newPaint()
        val out = createBitmap(size, size)
        val canvas = Canvas(out)
        val inset = (size * spec.insetPercent / 100f).roundToInt()

        if (drawable is AdaptiveIconDrawable) {
            // Adaptive layers are 108dp with a 72dp safe zone: extend bounds by 1/6 on each side.
            val content = createBitmap(size, size)
            val c = Canvas(content)
            val ext = (size - 2 * inset) / 4
            val bounds = Rect(inset - ext, inset - ext, size - inset + ext, size - inset + ext)
            val mono = if (spec.themed && Build.VERSION.SDK_INT >= 33) drawable.monochrome else null
            if (mono != null) {
                c.drawColor(spec.themedBackground)
                mono.mutate()
                mono.setTint(spec.themedForeground)
                mono.bounds = bounds
                mono.draw(c)
            } else {
                if (inset > 0) c.drawColor(backgroundSample(drawable))
                drawable.background?.let { it.bounds = bounds; it.draw(c) }
                drawable.foreground?.let { it.bounds = bounds; it.draw(c) }
            }
            canvas.drawPath(shapePath, maskPaint)
            maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(content, 0f, 0f, maskPaint)
            maskPaint.xfermode = null
            content.recycle()
            return out
        }

        if (!spec.shapeLegacy) return drawToBitmap(drawable, size, inset = spec.insetPercent)

        // Legacy icon: draw it on a filled shape.
        maskPaint.color = spec.legacyBackground
        canvas.drawPath(shapePath, maskPaint)
        maskPaint.color = 0xFF000000.toInt()
        val pad = (size * 0.18f).roundToInt() + inset / 2
        drawable.mutate()
        drawable.setBounds(pad, pad, size - pad, size - pad)
        val save = canvas.save()
        canvas.clipPath(shapePath)
        drawable.draw(canvas)
        canvas.restoreToCount(save)
        return out
    }

    private fun themedSystem(drawable: AdaptiveIconDrawable, spec: IconSpec): Bitmap {
        val themed = AdaptiveIconDrawable(
            android.graphics.drawable.ColorDrawable(spec.themedBackground),
            drawable.monochrome!!.mutate().apply { setTint(spec.themedForeground) },
        )
        return drawToBitmap(themed, spec.sizePx, inset = spec.insetPercent)
    }

    private fun backgroundSample(d: AdaptiveIconDrawable): Int {
        val bg = d.background ?: return 0xFFFFFFFF.toInt()
        val bmp = createBitmap(4, 4)
        bg.setBounds(0, 0, 4, 4)
        bg.draw(Canvas(bmp))
        return bmp.getPixel(2, 2).also { bmp.recycle() }
    }

    /** ADW "iconback / iconmask / iconupon / scale" composition for apps the pack does not theme. */
    private fun renderWithPackMask(base: Drawable, pack: IconPackManager.LoadedPack, spec: IconSpec): Bitmap {
        val size = spec.sizePx
        val maskPaint = newPaint()
        val out = createBitmap(size, size)
        val canvas = Canvas(out)
        val backName = pack.backs[(base.hashCode() and 0x7fffffff) % pack.backs.size]
        pack.drawable(backName, density)?.let { it.setBounds(0, 0, size, size); it.draw(canvas) }
        val inner = (size * pack.scale).roundToInt()
        val offset = (size - inner) / 2
        val iconBmp = drawToBitmap(base, inner)
        canvas.drawBitmap(iconBmp, offset.toFloat(), offset.toFloat(), maskPaint)
        pack.mask?.let { pack.drawable(it, density) }?.let { mask ->
            val maskBmp = drawToBitmap(mask, size)
            maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            canvas.drawBitmap(maskBmp, 0f, 0f, maskPaint)
            maskPaint.xfermode = null
        }
        pack.upon?.let { pack.drawable(it, density) }?.let { it.setBounds(0, 0, size, size); it.draw(canvas) }
        return out
    }

    private fun drawToBitmap(drawable: Drawable, size: Int, inset: Int = 0): Bitmap {
        val bmp = createBitmap(size, size)
        val canvas = Canvas(bmp)
        val pad = (size * inset / 100f).roundToInt()
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        // Keep the aspect ratio of non-square drawables.
        if (w > 0 && h > 0 && w != h && drawable !is AdaptiveIconDrawable) {
            val avail = size - 2 * pad
            val scale = avail / maxOf(w, h).toFloat()
            val dw = (w * scale).roundToInt()
            val dh = (h * scale).roundToInt()
            drawable.setBounds((size - dw) / 2, (size - dh) / 2, (size + dw) / 2, (size + dh) / 2)
        } else {
            drawable.setBounds(pad, pad, size - pad, size - pad)
        }
        drawable.draw(canvas)
        return bmp
    }

    private fun loadFileBitmap(path: String, size: Int): Bitmap? = runCatching {
        val file = File(path)
        if (!file.exists()) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (opts.outWidth / (sample * 2) >= size && opts.outHeight / (sample * 2) >= size) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
