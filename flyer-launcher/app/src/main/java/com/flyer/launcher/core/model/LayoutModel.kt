package com.flyer.launcher.core.model

import androidx.compose.runtime.Immutable
import com.flyer.launcher.core.config.ColorValue
import com.flyer.launcher.core.config.FolderPreview
import com.flyer.launcher.core.config.IconShape
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** A cell rectangle on a workspace grid. */
@Immutable
@Serializable
data class Cell(val x: Int = 0, val y: Int = 0, val spanX: Int = 1, val spanY: Int = 1) {
    fun overlaps(o: Cell): Boolean =
        x < o.x + o.spanX && o.x < x + spanX && y < o.y + o.spanY && o.y < y + spanY

    fun fitsIn(columns: Int, rows: Int): Boolean =
        x >= 0 && y >= 0 && spanX >= 1 && spanY >= 1 && x + spanX <= columns && y + spanY <= rows
}

/**
 * Free position of an item, expressed as a fraction (0..1) of the workspace content area so the
 * layout survives rotation and grid changes.
 */
@Immutable
@Serializable
data class FreePos(val x: Float = 0f, val y: Float = 0f)

/** Per-item visual overrides edited through the Flyer inspector. */
@Immutable
@Serializable
data class ItemStyle(
    val hideLabel: Boolean? = null,
    val iconScale: Float? = null,
    val labelColor: ColorValue? = null,
    val rotationDeg: Float? = null,
    val opacity: Float? = null,
) {
    val isEmpty: Boolean
        get() = hideLabel == null && iconScale == null && labelColor == null && rotationDeg == null && opacity == null
}

@Serializable
sealed interface WorkspaceItem {
    val id: String
    val cell: Cell
    val free: FreePos?
    val style: ItemStyle

    fun withCell(cell: Cell): WorkspaceItem
    fun withFree(free: FreePos?): WorkspaceItem
    fun withStyle(style: ItemStyle): WorkspaceItem
}

/** An app (or built-in launcher app such as Flyer) placed on a surface. */
@Immutable
@Serializable
@SerialName("app")
data class AppItem(
    override val id: String = newId(),
    val appKey: String,
    override val cell: Cell = Cell(),
    override val free: FreePos? = null,
    override val style: ItemStyle = ItemStyle(),
) : WorkspaceItem {
    override fun withCell(cell: Cell) = copy(cell = cell)
    override fun withFree(free: FreePos?) = copy(free = free)
    override fun withStyle(style: ItemStyle) = copy(style = style)
}

@Immutable
@Serializable
@SerialName("folder")
data class FolderItem(
    override val id: String = newId(),
    val name: String = "Folder",
    val apps: List<String> = emptyList(),
    override val cell: Cell = Cell(),
    override val free: FreePos? = null,
    override val style: ItemStyle = ItemStyle(),
    val color: ColorValue? = null,
    val shape: IconShape? = null,
    val preview: FolderPreview? = null,
    val columns: Int? = null,
) : WorkspaceItem {
    override fun withCell(cell: Cell) = copy(cell = cell)
    override fun withFree(free: FreePos?) = copy(free = free)
    override fun withStyle(style: ItemStyle) = copy(style = style)
}

@Immutable
@Serializable
@SerialName("widget")
data class WidgetItem(
    override val id: String = newId(),
    /** Device-specific id allocated by the AppWidgetHost; -1 means "needs to be re-bound". */
    val appWidgetId: Int,
    /** Flattened provider ComponentName, used to re-bind after a restore. */
    val provider: String,
    val providerUser: Long = 0,
    override val cell: Cell = Cell(spanX = 2, spanY = 2),
    override val free: FreePos? = null,
    override val style: ItemStyle = ItemStyle(),
    val paddingDp: Int? = null,
    val background: ColorValue? = null,
    val cornerRadiusDp: Int? = null,
) : WorkspaceItem {
    override fun withCell(cell: Cell) = copy(cell = cell)
    override fun withFree(free: FreePos?) = copy(free = free)
    override fun withStyle(style: ItemStyle) = copy(style = style)
}

@Serializable enum class BuiltInWidgetKind { CLOCK, SEARCH, DATE, FLYER_QUICK_PANEL }

/** Launcher-owned widgets that are rendered and fully customized by the launcher itself. */
@Immutable
@Serializable
@SerialName("builtin")
data class BuiltInWidgetItem(
    override val id: String = newId(),
    val kind: BuiltInWidgetKind,
    override val cell: Cell = Cell(spanX = 4, spanY = 1),
    override val free: FreePos? = null,
    override val style: ItemStyle = ItemStyle(),
) : WorkspaceItem {
    override fun withCell(cell: Cell) = copy(cell = cell)
    override fun withFree(free: FreePos?) = copy(free = free)
    override fun withStyle(style: ItemStyle) = copy(style = style)
}

/** A pinned / deep shortcut published by an app (requires the launcher to be the default home). */
@Immutable
@Serializable
@SerialName("shortcut")
data class ShortcutItem(
    override val id: String = newId(),
    val packageName: String,
    val shortcutId: String,
    val userSerial: Long = 0,
    val label: String,
    override val cell: Cell = Cell(),
    override val free: FreePos? = null,
    override val style: ItemStyle = ItemStyle(),
) : WorkspaceItem {
    override fun withCell(cell: Cell) = copy(cell = cell)
    override fun withFree(free: FreePos?) = copy(free = free)
    override fun withStyle(style: ItemStyle) = copy(style = style)
}

@Immutable
@Serializable
data class HomePage(
    val id: String = newId(),
    val items: List<WorkspaceItem> = emptyList(),
)

/** User-created groups shown as tabs in the app drawer. */
@Immutable
@Serializable
data class DrawerGroup(
    val id: String = newId(),
    val name: String,
    val apps: List<String> = emptyList(),
)

/** Per-app overrides that apply everywhere the app is shown (home, dock, drawer, search). */
@Immutable
@Serializable
data class AppOverride(
    val label: String? = null,
    val icon: IconSource? = null,
)

@Serializable
sealed interface IconSource {
    /** A drawable from an installed icon pack. */
    @Immutable
    @Serializable
    @SerialName("pack")
    data class Pack(val packPackage: String, val drawableName: String) : IconSource

    /** An image the user imported; stored in app-private storage. */
    @Immutable
    @Serializable
    @SerialName("image")
    data class Image(val path: String) : IconSource

    /** Re-use another app's icon. */
    @Immutable
    @Serializable
    @SerialName("app")
    data class OtherApp(val appKey: String) : IconSource
}

@Immutable
@Serializable
data class DesktopLayout(
    val items: List<WorkspaceItem> = emptyList(),
    val taskbarPins: List<String> = emptyList(),
)

/**
 * Everything the user placed on the launcher. Kept separate from [com.flyer.launcher.core.config.FlyerConfig]
 * so themes can be applied without touching the arrangement, while backups include both.
 */
@Immutable
@Serializable
data class HomeLayout(
    val pages: List<HomePage> = listOf(HomePage()),
    val dock: List<WorkspaceItem> = emptyList(),
    val desktop: DesktopLayout = DesktopLayout(),
    val hiddenApps: Set<String> = emptySet(),
    val drawerGroups: List<DrawerGroup> = emptyList(),
    val appOverrides: Map<String, AppOverride> = emptyMap(),
    /** True once the first-run default layout was generated. */
    val initialized: Boolean = false,
)

fun newId(): String = UUID.randomUUID().toString().substring(0, 13)

/** Where an item lives. Used by the workspace engine, drag & drop and the editor. */
@Immutable
sealed interface Surface {
    data class Page(val index: Int) : Surface
    data object Dock : Surface
    data object Desktop : Surface
    data class Folder(val folderId: String) : Surface
}
