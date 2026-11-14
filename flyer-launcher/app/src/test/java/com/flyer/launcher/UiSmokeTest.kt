package com.flyer.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flyer.launcher.core.config.DesktopMode
import com.flyer.launcher.flyer.FlyerCategory
import com.flyer.launcher.flyer.FlyerRoute
import com.flyer.launcher.launcher.LauncherActivity
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Launches the real HOME activity under Robolectric and walks through every launcher surface and
 * every Flyer module, rendering each to a PNG (build/flyer-screenshots). Any composition or
 * layout crash fails the test.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<LauncherActivity>()

    private val outDir: File by lazy {
        File(System.getProperty("flyer.screenshots") ?: "build/flyer-screenshots").apply { mkdirs() }
    }

    private fun snap(name: String) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(0xFF1B1B3A.toInt()) // stands in for the system wallpaper behind the window
            view.draw(canvas)
            File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun core() = FlyerApp.core(rule.activity)

    private fun onUi(block: LauncherActivity.() -> Unit) {
        rule.runOnUiThread { rule.activity.block() }
        rule.waitForIdle()
    }

    private fun awaitLayout() {
        rule.waitUntil(20_000) { core().document.value.layout.initialized }
        rule.waitForIdle()
    }

    @Test
    fun homeDrawerSearchAndFolderRender() {
        awaitLayout()
        val layout = core().document.value.layout
        assertTrue("default layout has a page", layout.pages.isNotEmpty())
        assertTrue("Flyer is a built-in app on the home screen", layout.pages.flatMap { it.items }.any { (it as? com.flyer.launcher.core.model.AppItem)?.appKey == "builtin:flyer" })
        snap("01-home")
        onUi { openDrawer() }
        snap("02-drawer")
        onUi { onBackPressedDispatcher.onBackPressed() }
        onUi { openSearch("fly") }
        snap("03-search")
        onUi { onBackPressedDispatcher.onBackPressed() }
        onUi { perform(com.flyer.launcher.core.config.GestureAction(com.flyer.launcher.core.config.ActionType.HOME_MENU)) }
        snap("04-home-menu")
    }

    @Test
    fun flyerModulesRender() {
        awaitLayout()
        onUi { openFlyer(FlyerRoute.Dashboard) }
        snap("10-flyer-dashboard")
        FlyerCategory.entries.filter { it != FlyerCategory.LAYOUT_EDITOR }.forEachIndexed { i, c ->
            onUi { openFlyer(FlyerRoute.Category(c)) }
            snap("%02d-flyer-%s".format(11 + i, c.name.lowercase()))
        }
        onUi { openFlyer(FlyerRoute.LiveEditor()) }
        snap("40-flyer-live-editor")
        onUi { openFlyer(FlyerRoute.Search) }
        snap("41-flyer-search")
        onUi { openFlyer(FlyerRoute.AppEditor("builtin:flyer")) }
        snap("42-flyer-app-editor")
        onUi { openFlyer(FlyerRoute.WidgetPicker) }
        snap("43-flyer-widget-picker")
    }

    @Test
    fun editingInFlyerAppliesToTheLauncher() {
        awaitLayout()
        onUi { openFlyer(FlyerRoute.Category(FlyerCategory.ICONS)) }
        val session = rule.activity.let { a ->
            val field = LauncherActivity::class.java.getDeclaredField("ui").apply { isAccessible = true }
            (field.get(a) as com.flyer.launcher.launcher.LauncherUiState).flyer.session!!
        }
        rule.runOnUiThread {
            session.updateConfig("Icon size", null) { it.copy(icons = it.icons.copy(sizeDp = 72)) }
            session.updateConfig("Columns", null) { it.copy(home = it.home.copy(columns = 5)) }
        }
        rule.waitForIdle()
        assertTrue(session.isDirty)
        assertTrue("draft is not live before apply", core().config.value.icons.sizeDp != 72)
        rule.runOnUiThread { session.undo() }
        rule.waitForIdle()
        assertTrue(session.draft.config.home.columns != 5)
        rule.runOnUiThread { session.redo(); session.apply() }
        rule.waitForIdle()
        assertTrue(core().config.value.icons.sizeDp == 72)
        assertTrue(core().config.value.home.columns == 5)
        assertTrue(!session.isDirty)
        onUi { onBackPressedDispatcher.onBackPressed() }
        onUi { onBackPressedDispatcher.onBackPressed() }
        snap("50-home-after-apply")
    }

    @Test
    fun desktopModeRenders() {
        awaitLayout()
        rule.runOnUiThread {
            core().editHome("Desktop on") { d -> d.copy(config = d.config.copy(desktop = d.config.desktop.copy(mode = DesktopMode.ON))) }
        }
        rule.waitForIdle()
        snap("60-desktop")
        onUi { openDrawer() }
        snap("61-desktop-start-menu")
    }

    private fun flyerItemOnPage0(): com.flyer.launcher.core.model.AppItem =
        core().document.value.layout.pages[0].items.filterIsInstance<com.flyer.launcher.core.model.AppItem>().first { it.appKey == "builtin:flyer" }

    @Test
    fun longPressAndDragMovesAnIconOnTheHomeScreen() {
        awaitLayout()
        val before = flyerItemOnPage0()
        rule.onNodeWithText("Flyer").performTouchInput {
            down(center)
            advanceEventTime(1200) // long press
            repeat(12) { moveBy(androidx.compose.ui.geometry.Offset(0f, -70f)); advanceEventTime(16) }
            up()
        }
        rule.waitForIdle()
        val after = flyerItemOnPage0()
        assertTrue("icon should have moved up: $before -> $after", after.cell.y < before.cell.y)
        assertTrue("undo is offered for the edit", core().lastHomeEdit.value != null)
        snap("70-home-after-drag")
    }

    @Test
    fun droppingAnAppOntoAnotherCreatesAFolder() {
        awaitLayout()
        val flyer = flyerItemOnPage0()
        rule.runOnUiThread {
            core().editHome("setup") { d ->
                val l = d.layout
                val page = l.pages[0]
                val editor = com.flyer.launcher.core.model.AppItem(appKey = "builtin:editor", cell = com.flyer.launcher.core.model.Cell(flyer.cell.x, flyer.cell.y - 2))
                d.copy(layout = l.copy(pages = listOf(page.copy(items = page.items + editor)) + l.pages.drop(1)))
            }
        }
        rule.waitForIdle()
        val target = rule.onNodeWithText("Layout Editor").fetchSemanticsNode().boundsInRoot
        val source = rule.onNodeWithText("Flyer").fetchSemanticsNode().boundsInRoot
        // Aim for the centre of the target icon (above its label).
        val dy = (target.top - 2 * (target.bottom - target.top)) - source.top
        rule.onNodeWithText("Flyer").performTouchInput {
            down(center)
            advanceEventTime(1200)
            val steps = 10
            repeat(steps) { moveBy(androidx.compose.ui.geometry.Offset(0f, dy / steps)); advanceEventTime(16) }
            up()
        }
        rule.waitForIdle()
        val items = core().document.value.layout.pages[0].items
        val folder = items.filterIsInstance<com.flyer.launcher.core.model.FolderItem>().singleOrNull()
        assertTrue("expected a folder, got $items", folder != null && folder.apps.toSet() == setOf("builtin:flyer", "builtin:editor"))
        onUi { openFolder(folder!!.id, null) }
        snap("71-folder-open")
    }

    @Test
    fun liveEditorDragMovesItemInDraftOnly() {
        awaitLayout()
        val before = flyerItemOnPage0()
        onUi { openFlyer(FlyerRoute.LiveEditor()) }
        rule.onAllNodesWithText("Flyer")[0].performTouchInput {
            down(center)
            repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(0f, -40f)); advanceEventTime(16) }
            up()
        }
        rule.waitForIdle()
        val session = LauncherActivity::class.java.getDeclaredField("ui").apply { isAccessible = true }
            .get(rule.activity).let { (it as com.flyer.launcher.launcher.LauncherUiState).flyer.session!! }
        val draft = session.draft.layout.pages[0].items.filterIsInstance<com.flyer.launcher.core.model.AppItem>().first { it.appKey == "builtin:flyer" }
        assertTrue("editor drag should move the draft item: $before -> $draft", draft.cell.y < before.cell.y)
        assertTrue("committed layout is untouched until Apply", flyerItemOnPage0().cell == before.cell)
        snap("72-editor-after-drag")
    }

    private fun uiState() = LauncherActivity::class.java.getDeclaredField("ui").apply { isAccessible = true }
        .get(rule.activity) as com.flyer.launcher.launcher.LauncherUiState

    @Test
    fun lockButtonLocksAndPinUnlocks() {
        awaitLayout()
        kotlinx.coroutines.runBlocking { core().lock.setCredential(com.flyer.launcher.core.lock.CredentialType.PIN, "1234") }
        rule.runOnUiThread {
            core().editHome("lock setup") { d ->
                // Blur off: exercises the faded-home fallback (software rendering can't blur anyway).
                d.copy(config = d.config.copy(
                    lock = d.config.lock.copy(showButton = true, unlockMethod = com.flyer.launcher.core.config.UnlockMethod.LAUNCHER_PIN, awayMessage = "Back in 5 minutes"),
                    performance = d.config.performance.copy(blurEffects = false),
                ))
            }
        }
        rule.waitForIdle()
        snap("80-lock-button")
        rule.onNodeWithContentDescription("Lock (hold to lock the phone)").performClick()
        rule.waitUntil(5_000) { core().lock.state.value.locked }
        snap("81-locked")
        // Home is not usable while locked: tapping the Flyer icon underneath does nothing.
        rule.onNodeWithText("Flyer").performClick()
        rule.waitForIdle()
        assertTrue(!uiState().flyer.isOpen)
        rule.onNodeWithText("Unlock").performClick()
        rule.waitForIdle()
        listOf("9", "9", "9", "9").forEach { rule.onNodeWithText(it).performClick() }
        rule.onNodeWithContentDescription("Unlock").performClick()
        rule.waitUntil(10_000) { core().lock.state.value.failedAttempts == 1 }
        rule.waitForIdle()
        snap("82-wrong-pin")
        assertTrue(core().lock.state.value.locked)
        listOf("1", "2", "3", "4").forEach { rule.onNodeWithText(it).performClick() }
        rule.onNodeWithContentDescription("Unlock").performClick()
        rule.waitUntil(10_000) { !core().lock.state.value.locked }
        rule.waitForIdle()
        assertTrue(!core().lock.state.value.locked)
    }

    @Test
    fun builtInFlyerHasNoRemoveEntry() {
        awaitLayout()
        rule.onNodeWithText("Flyer").performTouchInput { down(center); advanceEventTime(1200); up() }
        rule.waitForIdle()
        rule.onNodeWithText("Customize icon & label").assertExists()
        rule.onNodeWithText("Remove").assertDoesNotExist()
        rule.onNodeWithText("Uninstall").assertDoesNotExist()
    }

    @Test
    fun advancedThemesRender() {
        awaitLayout()
        com.flyer.launcher.core.theme.ThemePresets.advanced.forEachIndexed { i, preset ->
            rule.runOnUiThread { core().editHome(preset.name) { d -> d.copy(config = preset.apply(d.config)) } }
            rule.waitForIdle()
            snap("%02d-theme-%s".format(90 + i, preset.id))
            if (preset.id == "liquid-glass") {
                onUi { openDrawer() }
                snap("90b-liquid-glass-drawer")
                onUi { onBackPressedDispatcher.onBackPressed() }
                onUi { perform(com.flyer.launcher.core.config.GestureAction(com.flyer.launcher.core.config.ActionType.HOME_MENU)) }
                snap("90c-liquid-glass-menu")
                onUi { onBackPressedDispatcher.onBackPressed() }
            }
        }
        onUi { openFlyer(FlyerRoute.Category(FlyerCategory.THEMES)) }
        snap("99a-flyer-themes")
        onUi { openFlyer(FlyerRoute.Category(FlyerCategory.MATERIALS)) }
        snap("99b-flyer-materials")
        onUi { openFlyer(FlyerRoute.Category(FlyerCategory.LOCK)) }
        snap("99c-flyer-lock")
    }

    @Test
    fun copyOriginalLayoutDialogOpens() {
        awaitLayout()
        onUi { openFlyer(FlyerRoute.Category(FlyerCategory.HOME)) }
        rule.onNodeWithText("Copy original home screen layout").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Copy original home screen").fetchSemanticsNodes().isNotEmpty() }
        snap("98-copy-layout-dialog")
    }
}
