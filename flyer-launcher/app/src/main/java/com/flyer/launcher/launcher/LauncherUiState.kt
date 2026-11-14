package com.flyer.launcher.launcher

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import com.flyer.launcher.core.LauncherCore
import com.flyer.launcher.core.model.Surface
import com.flyer.launcher.flyer.FlyerController
import com.flyer.launcher.launcher.drag.DragController
import com.flyer.launcher.launcher.ui.GestureArbiter

@Immutable
data class MenuEntry(
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val shortcutHint: String? = null,
    val onClick: () -> Unit,
)

@Immutable
data class MenuState(
    val anchor: Rect,
    val title: String? = null,
    val entries: List<MenuEntry>,
    /** Secondary row (e.g. app shortcuts) shown above the main entries. */
    val shortcuts: List<MenuEntry> = emptyList(),
)

@Immutable
data class ConfirmRequest(
    val title: String,
    val message: String,
    val confirmLabel: String = "Remove",
    val onConfirm: () -> Unit,
)

/** A request to unlock before doing something (open Flyer, open a protected app). */
@Immutable
data class AuthRequest(val title: String, val onSuccess: () -> Unit)

@Immutable
data class OpenFolder(val folderId: String, val anchor: Rect?)

/** Pager control registered by the home screen so actions/gestures can switch pages. */
interface PageController {
    val pageCount: Int
    val currentPage: Int
    fun goTo(page: Int, animate: Boolean = true)
}

/**
 * Transient UI state of the launcher (which overlays are open, menus, drag & drop, Flyer).
 * Persistent state lives in [LauncherCore]; nothing here needs to survive process death.
 */
@Stable
class LauncherUiState(val core: LauncherCore) {
    var drawerOpen by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var openFolder by mutableStateOf<OpenFolder?>(null)
    var menu by mutableStateOf<MenuState?>(null)
    var widgetPicker by mutableStateOf<Surface?>(null)
    var resizingWidgetId by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var startMenuOpen by mutableStateOf(false)
    var renameFolderId by mutableStateOf<String?>(null)
    var confirm by mutableStateOf<ConfirmRequest?>(null)
    var authRequest by mutableStateOf<AuthRequest?>(null)
    var desktopActive by mutableStateOf(false)
    var resumed by mutableStateOf(true)

    /** Where the last home gesture happened (anchors the home menu). */
    var lastGesturePos: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero

    val drag = DragController()
    val arbiter = GestureArbiter()
    val flyer = FlyerController(core)

    var pageController: PageController? = null

    /** Hooks provided by the activity for flows that need an Activity (results, system pickers). */
    var reconfigureWidget: ((Int) -> Unit)? = null
    var openWallpaperPicker: (() -> Unit)? = null
    var restoreWidget: ((com.flyer.launcher.core.model.WidgetItem) -> Unit)? = null

    // Lock hooks (implemented by the activity, which owns the system prompts).
    var lockLauncher: (() -> Unit)? = null
    var lockPhone: (() -> Unit)? = null
    var canLockPhone: (() -> Boolean)? = null
    var unlockWithPhoneOrTap: ((com.flyer.launcher.core.config.UnlockMethod) -> Unit)? = null
    var finishUnlock: (() -> Unit)? = null
    var requestPhoneCredential: ((String, (Boolean) -> Unit) -> Unit)? = null

    val anyOverlayOpen: Boolean
        get() = drawerOpen || searchOpen || openFolder != null || menu != null || widgetPicker != null ||
            resizingWidgetId != null || startMenuOpen || flyer.isOpen

    /** Closes the top-most overlay. Returns false if nothing was open. */
    fun back(): Boolean = when {
        menu != null -> { menu = null; true }
        confirm != null -> { confirm = null; true }
        renameFolderId != null -> { renameFolderId = null; true }
        resizingWidgetId != null -> { resizingWidgetId = null; true }
        widgetPicker != null -> { widgetPicker = null; true }
        flyer.isOpen -> { flyer.back(); true }
        openFolder != null -> { openFolder = null; true }
        searchOpen -> { closeSearch(); true }
        startMenuOpen -> { startMenuOpen = false; true }
        drawerOpen -> { drawerOpen = false; true }
        else -> false
    }

    fun closeSearch() {
        searchOpen = false
        searchQuery = ""
    }

    /** Home button: close every launcher overlay (Flyer optionally stays open). */
    fun closeAll(includeFlyer: Boolean) {
        menu = null
        openFolder = null
        closeSearch()
        drawerOpen = false
        startMenuOpen = false
        widgetPicker = null
        resizingWidgetId = null
        renameFolderId = null
        drag.cancel()
        if (includeFlyer) flyer.requestClose()
    }

    fun toast(text: String) {
        message = text
    }
}
