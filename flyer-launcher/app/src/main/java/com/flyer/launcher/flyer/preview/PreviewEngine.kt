package com.flyer.launcher.flyer.preview

import android.app.WallpaperManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.flyer.launcher.core.apps.AppEntry
import com.flyer.launcher.core.config.BackgroundType
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.Positioning
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.persistence.LauncherDocument
import com.flyer.launcher.core.theme.LocalFlyerConfig
import com.flyer.launcher.launcher.LocalLauncherActions
import com.flyer.launcher.launcher.NoOpLauncherActions
import com.flyer.launcher.launcher.drawer.AppDrawer
import com.flyer.launcher.launcher.home.DockContainer
import com.flyer.launcher.launcher.home.HomeBackground
import com.flyer.launcher.launcher.home.HomeFrame
import com.flyer.launcher.launcher.home.PageIndicator
import com.flyer.launcher.launcher.home.StaticItemVisual
import com.flyer.launcher.launcher.home.WorkspaceSurface
import com.flyer.launcher.launcher.home.rememberDockBackdrop
import com.flyer.launcher.launcher.overlays.FolderPopup
import com.flyer.launcher.launcher.render.LocalAppOverrides
import com.flyer.launcher.launcher.render.LocalDraggingIds
import com.flyer.launcher.launcher.render.LocalRenderMode
import com.flyer.launcher.launcher.render.RenderMode
import com.flyer.launcher.launcher.render.SearchBarWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Size of the real launcher window, so previews are rendered at true scale and then shrunk. */
@Composable
fun rememberScreenSize(): IntSize {
    val root = LocalView.current.rootView
    val dm = LocalContext.current.resources.displayMetrics
    return IntSize(root.width.takeIf { it > 0 } ?: dm.widthPixels, root.height.takeIf { it > 0 } ?: dm.heightPixels)
}

/**
 * Renders [content] at the real screen size and scales it down to fit [targetHeightDp] (or width).
 * Pointer input inside the content receives unscaled coordinates, which the editor relies on.
 */
@Composable
fun ScaledScreen(screen: IntSize, scale: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.layout { measurable, _ ->
            val p = measurable.measure(Constraints.fixed(screen.width, screen.height))
            val w = (screen.width * scale).toInt()
            val h = (screen.height * scale).toInt()
            layout(w, h) {
                p.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                    clip = true
                }
            }
        },
    ) { Box(Modifier.fillMaxSize(), content = content) }
}

/** Provides the draft document to launcher renderers and disables every launcher action. */
@Composable
fun PreviewLocals(doc: LauncherDocument, mode: RenderMode = RenderMode.PREVIEW, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalFlyerConfig provides doc.config,
        LocalAppOverrides provides doc.layout.appOverrides,
        LocalRenderMode provides mode,
        LocalLauncherActions provides NoOpLauncherActions,
        LocalDraggingIds provides emptySet(),
        content = content,
    )
}

private sealed interface WallpaperStandIn {
    data class Bitmap(val image: ImageBitmap) : WallpaperStandIn
    data class Colors(val colors: List<Color>) : WallpaperStandIn
}

/**
 * Third-party apps generally cannot read the system wallpaper on modern Android. The preview uses
 * the real image when the platform allows it, otherwise a gradient of the wallpaper's own colors.
 */
@Composable
fun WallpaperBackdrop(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val standIn by produceState<WallpaperStandIn?>(null) {
        value = withContext(Dispatchers.IO) {
            val wm = WallpaperManager.getInstance(context)
            val image = runCatching {
                @Suppress("MissingPermission")
                wm.drawable?.let { d ->
                    val w = d.intrinsicWidth.coerceIn(1, 2000)
                    val h = d.intrinsicHeight.coerceIn(1, 2000)
                    val scale = (720f / maxOf(w, h)).coerceAtMost(1f)
                    d.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
                }
            }.getOrNull()
            if (image != null) WallpaperStandIn.Bitmap(image)
            else {
                val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    runCatching { wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()?.let { wc ->
                        listOfNotNull(wc.primaryColor, wc.secondaryColor, wc.tertiaryColor).map { Color(it.toArgb()) }
                    }
                } else null
                WallpaperStandIn.Colors(colors?.takeIf { it.isNotEmpty() } ?: listOf(Color(0xFF1B1B3A), Color(0xFF3A2D8F), Color(0xFF0E5E7A)))
            }
        }
    }
    when (val s = standIn) {
        is WallpaperStandIn.Bitmap -> Image(s.image, null, contentScale = ContentScale.Crop, modifier = modifier.fillMaxSize())
        is WallpaperStandIn.Colors -> Canvas(modifier.fillMaxSize()) {
            val list = if (s.colors.size == 1) s.colors + s.colors.first().copy(alpha = 0.6f) else s.colors
            drawRect(Brush.linearGradient(list, start = androidx.compose.ui.geometry.Offset.Zero, end = androidx.compose.ui.geometry.Offset(size.width, size.height)))
        }
        null -> Box(modifier.fillMaxSize().background(Color(0xFF1B1B3A)))
    }
}

/** The background exactly as the launcher draws it, with a wallpaper stand-in where needed. */
@Composable
fun PreviewBackground(config: FlyerConfig) {
    if (config.home.background.type == BackgroundType.SYSTEM_WALLPAPER) WallpaperBackdrop()
    HomeBackground(config.home.background)
}

/** Static home screen: the same frame, surfaces and visuals as the live launcher. */
@Composable
fun StaticHome(config: FlyerConfig, layout: HomeLayout, page: Int, insets: PaddingValues) {
    Box(Modifier.fillMaxSize()) {
        PreviewBackground(config)
        HomeFrame(
            config = config,
            insets = insets,
            pages = { m ->
                Box(m) {
                    WorkspaceSurface(
                        items = layout.pages.getOrNull(page)?.items.orEmpty(),
                        columns = config.home.columns, rows = config.home.rows, home = config.home,
                        freePositioning = config.home.positioning == Positioning.FREE,
                    ) { item, geometry, _ -> StaticItemVisual(item, geometry.cellWidth, geometry.cellHeight) }
                }
            },
            indicator = { PageIndicator(config, layout.pages.size, page.toFloat()) },
            searchBar = { SearchBarWidget(Modifier.height(config.search.barHeightDp.dp + 8.dp)) },
            dock = { vertical -> StaticDock(config, layout, vertical) },
        )
    }
}

@Composable
fun StaticDock(config: FlyerConfig, layout: HomeLayout, vertical: Boolean, modifier: Modifier = Modifier) {
    val iconSize = (if (config.dock.followIconSize) config.icons.sizeDp else config.dock.iconSizeDp).dp
        .coerceAtMost((config.dock.thicknessDp - 2 * config.dock.marginDp - if (config.dock.showLabels) 20 else 0).coerceAtLeast(20).dp)
    val density = LocalDensity.current
    val cellPx = with(density) { (iconSize + 12.dp).toPx() }
    val cellH = with(density) { (config.dock.thicknessDp - 2 * config.dock.marginDp).dp.toPx() }
    DockContainer(config, vertical, modifier, backdrop = rememberDockBackdrop(config)) {
        layout.dock.sortedBy { it.cell.x }.forEach { item ->
            key(item.id) {
                Box(Modifier.size(with(density) { cellPx.toDp() }, with(density) { cellH.toDp() }), contentAlignment = Alignment.Center) {
                    StaticItemVisual(item, cellPx, cellH, showLabels = config.dock.showLabels)
                }
            }
        }
    }
}

/** Kind of preview shown at the top of a Flyer category. */
enum class PreviewKind { HOME, DRAWER, FOLDER, DESKTOP, NONE }

/** A complete, scaled preview of one launcher surface for the given draft. */
@Composable
fun SurfacePreview(
    kind: PreviewKind,
    doc: LauncherDocument,
    apps: List<AppEntry>,
    page: Int,
    insets: PaddingValues,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val screen = rememberScreenSize()
    val corner = (28 / scale).dp
    PreviewLocals(doc) {
        ScaledScreen(screen, scale, modifier.clip(RoundedCornerShape(18.dp))) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(corner))) {
                when (kind) {
                    PreviewKind.HOME, PreviewKind.NONE -> StaticHome(doc.config, doc.layout, page.coerceIn(0, doc.layout.pages.lastIndex), insets)
                    PreviewKind.DRAWER -> {
                        PreviewBackground(doc.config)
                        AppDrawer(null, apps, doc.layout, emptyMap(), emptyMap(), interactive = false, onClose = {}, insets = insets)
                    }
                    PreviewKind.FOLDER -> {
                        StaticHome(doc.config, doc.layout, page.coerceIn(0, doc.layout.pages.lastIndex), insets)
                        val folder = findAnyFolder(doc.layout) ?: sampleFolder(apps)
                        FolderPopup(null, folder, null, interactive = false, asWindow = false, onDismiss = {})
                    }
                    PreviewKind.DESKTOP -> DesktopPreview(doc.config, doc.layout, insets)
                }
            }
        }
    }
}

fun findAnyFolder(layout: HomeLayout): FolderItem? =
    (layout.pages.flatMap { it.items } + layout.dock + layout.desktop.items).filterIsInstance<FolderItem>().firstOrNull()

fun sampleFolder(apps: List<AppEntry>) = FolderItem(id = "sample", name = "Sample folder", apps = apps.take(6).map { it.key })
