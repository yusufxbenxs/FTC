package com.flyer.launcher.flyer.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.TaskbarPosition
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.launcher.home.StaticItemVisual
import com.flyer.launcher.launcher.home.WorkspaceSurface
import com.flyer.launcher.launcher.home.fittedIconSize
import com.flyer.launcher.launcher.render.AppTile
import com.flyer.launcher.launcher.render.IconImage
import com.flyer.launcher.launcher.render.rememberAppIcon

@Composable
fun DesktopPreview(config: FlyerConfig, layout: HomeLayout, insets: PaddingValues) {
    val d = config.desktop
    Box(Modifier.fillMaxSize()) {
        PreviewBackground(config)
        Column(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding())) {
            if (d.taskbarPosition == TaskbarPosition.TOP) StaticTaskbar(config, layout)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                WorkspaceSurface(
                    items = layout.desktop.items, columns = d.columns, rows = d.rows, home = null,
                    freePositioning = !d.snapToGrid, marginsDp = floatArrayOf(12f, 12f, 12f, 12f),
                ) { item, geometry, _ ->
                    if (item is AppItem) {
                        val size = fittedIconSize(config, geometry.cellWidth, geometry.cellHeight, true, d.iconSizeDp)
                        AppTile(item.appKey, size, true, config.icons.label, style = item.style, labelColor = d.labelColor.color)
                    } else StaticItemVisual(item, geometry.cellWidth, geometry.cellHeight)
                }
            }
            if (d.taskbarPosition == TaskbarPosition.BOTTOM) StaticTaskbar(config, layout)
            Spacer(Modifier.height(insets.calculateBottomPadding()))
        }
    }
}

@Composable
fun StaticTaskbar(config: FlyerConfig, layout: HomeLayout) {
    val d = config.desktop
    Row(
        Modifier.fillMaxWidth().height(d.taskbarHeightDp.dp).background(d.taskbarBackground.color).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Apps, null, tint = Color.White, modifier = Modifier.padding(8.dp))
        Icon(Icons.Outlined.Search, null, tint = Color.White, modifier = Modifier.padding(8.dp))
        Row(
            Modifier.weight(1f).fillMaxHeight(),
            horizontalArrangement = if (d.taskbarCentered) Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            layout.desktop.taskbarPins.forEach { key -> IconImage(rememberAppIcon(key, 32.dp), 32.dp, Modifier.padding(6.dp)) }
        }
        Icon(Icons.Outlined.Tune, null, tint = Color.White, modifier = Modifier.padding(8.dp).size(22.dp))
        if (d.taskbarShowClock) Text("12:00", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp))
    }
}
