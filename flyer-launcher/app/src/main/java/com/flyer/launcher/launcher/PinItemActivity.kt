package com.flyer.launcher.launcher

import android.appwidget.AppWidgetManager
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.flyer.launcher.FlyerApp
import com.flyer.launcher.R
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.ShortcutItem
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.theme.FlyerTheme
import com.flyer.launcher.core.workspace.WorkspaceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Confirms "Add to home screen" requests that apps send to the default launcher
 * (pinned shortcuts from browsers/maps/contacts and pinned widgets).
 */
class PinItemActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull()
        if (request == null || !request.isValid) {
            finish()
            return
        }
        val core = FlyerApp.core(this)
        if (core.lock.state.value.locked) {
            android.widget.Toast.makeText(this, "Flyer Launcher is locked", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val isShortcut = request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT
        val title = if (isShortcut) request.shortcutInfo?.shortLabel?.toString() ?: "Shortcut"
        else request.getAppWidgetProviderInfo(this)?.loadLabel(packageManager) ?: "Widget"

        setContent {
            val config by core.config.collectAsState()
            FlyerTheme(config) {
                val icon by produceState<ImageBitmap?>(null) {
                    value = withContext(Dispatchers.IO) {
                        val density = resources.displayMetrics.densityDpi
                        val d = if (isShortcut) request.shortcutInfo?.let { runCatching { launcherApps.getShortcutIconDrawable(it, density) }.getOrNull() }
                        else request.getAppWidgetProviderInfo(this@PinItemActivity)?.let { runCatching { it.loadPreviewImage(this@PinItemActivity, density) ?: it.loadIcon(this@PinItemActivity, density) }.getOrNull() }
                        d?.let { runCatching { it.toBitmap(it.intrinsicWidth.coerceIn(1, 512), it.intrinsicHeight.coerceIn(1, 512)).asImageBitmap() }.getOrNull() }
                    }
                }
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(getString(R.string.pin_add_title), style = MaterialTheme.typography.titleLarge)
                        icon?.let { Image(it, null, Modifier.size(if (isShortcut) 64.dp else 140.dp)) }
                        Text(title, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { finish() }) { Text("Cancel") }
                            Button(onClick = { accept(request, isShortcut, title); finish() }) { Text("Add") }
                        }
                    }
                }
            }
        }
    }

    private fun accept(request: LauncherApps.PinItemRequest, isShortcut: Boolean, title: String) {
        val core = FlyerApp.core(this)
        if (isShortcut) {
            val info = request.shortcutInfo ?: return
            if (!request.accept()) return
            val serial = getSystemService(UserManager::class.java).getSerialNumberForUser(info.userHandle ?: Process.myUserHandle())
            val item = ShortcutItem(packageName = info.`package`, shortcutId = info.id, userSerial = serial, label = title)
            core.editHome("Add $title") { d -> d.copy(layout = WorkspaceEngine.addItem(d.layout, item, d.config)) }
        } else {
            val info = request.getAppWidgetProviderInfo(this) ?: return
            val id = core.widgets.allocateId()
            val ok = request.accept(Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id) })
            if (!ok) {
                core.widgets.deleteId(id)
                return
            }
            val serial = getSystemService(UserManager::class.java).getSerialNumberForUser(info.profile)
            val config = core.config.value
            val dm = resources.displayMetrics
            val spans = core.widgets.spansFor(
                info, dm.widthPixels / dm.density / config.home.columns, dm.heightPixels / dm.density * 0.7f / config.home.rows,
                config.home.columns, config.home.rows,
            )
            val item = WidgetItem(appWidgetId = id, provider = info.provider.flattenToShortString(), providerUser = serial, cell = Cell(spanX = spans.defaultX, spanY = spans.defaultY))
            core.editHome("Add widget") { d -> d.copy(layout = WorkspaceEngine.addItem(d.layout, item, d.config)) }
        }
    }
}
