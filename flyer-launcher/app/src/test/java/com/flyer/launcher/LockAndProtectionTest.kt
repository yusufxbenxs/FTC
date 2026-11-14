package com.flyer.launcher

import com.flyer.launcher.core.config.BackgroundType
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.HomeConfig
import com.flyer.launcher.core.config.LockConfig
import com.flyer.launcher.core.config.StyleConfig
import com.flyer.launcher.core.config.SurfaceMaterial
import com.flyer.launcher.core.config.UnlockMethod
import com.flyer.launcher.core.lock.CredentialHasher
import com.flyer.launcher.core.lock.LockEngine
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.model.HomePage
import com.flyer.launcher.core.persistence.DocumentCodec
import com.flyer.launcher.core.persistence.LauncherDocument
import com.flyer.launcher.core.theme.ThemePresets
import com.flyer.launcher.core.config.ConfigValidator
import com.flyer.launcher.core.workspace.LayoutValidator
import com.flyer.launcher.core.workspace.WorkspaceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LockAndProtectionTest {

    private val config = FlyerConfig(home = HomeConfig(columns = 4, rows = 4))
    private val flyer = "builtin:flyer"

    @Test
    fun credentialHashVerifiesOnlyTheRightSecret() {
        val h = CredentialHasher.hash("2468", iterations = 2_000)
        assertTrue(CredentialHasher.verify("2468", h))
        assertFalse(CredentialHasher.verify("2469", h))
        assertFalse(CredentialHasher.verify("", h))
        val other = CredentialHasher.hash("2468", iterations = 2_000)
        assertNotEquals("salted: same secret, different hash", h.hash, other.hash)
        assertFalse(CredentialHasher.verify("2468", h.copy(salt = "!!not base64!!")))
    }

    @Test
    fun credentialRules() {
        assertNotNull(CredentialHasher.validate("12a4", pin = true))
        assertNotNull(CredentialHasher.validate("123", pin = true))
        assertEquals(null, CredentialHasher.validate("1234", pin = true))
        assertNotNull(CredentialHasher.validate("abc", pin = false))
        assertEquals(null, CredentialHasher.validate("correct horse", pin = false))
    }

    @Test
    fun lockoutGrowsAndIsCapped() {
        assertEquals(30_000L, LockEngine.lockoutDuration(0))
        assertEquals(60_000L, LockEngine.lockoutDuration(1))
        assertEquals(120_000L, LockEngine.lockoutDuration(2))
        assertEquals(30 * 60_000L, LockEngine.lockoutDuration(50))
    }

    @Test
    fun builtInAppsCannotBeRemoved() {
        val l = HomeLayout(pages = listOf(HomePage(items = listOf(AppItem(id = "f", appKey = flyer, cell = Cell(0, 0)), AppItem(id = "x", appKey = "x", cell = Cell(1, 0))))))
        assertSame(l, WorkspaceEngine.removeItem(l, "f"))
        assertEquals(1, WorkspaceEngine.removeItem(l, "x").pages[0].items.size)
        assertSame(l, WorkspaceEngine.removeApp(l, flyer))
    }

    @Test
    fun removingAFolderKeepsBuiltInsInside() {
        val folder = FolderItem(id = "fold", apps = listOf("a", flyer, "b"), cell = Cell(2, 2))
        val out = WorkspaceEngine.removeItem(HomeLayout(pages = listOf(HomePage(items = listOf(folder)))), "fold")
        val kept = out.pages[0].items.single()
        assertTrue(kept is AppItem && kept.appKey == flyer && kept.cell == Cell(2, 2))
    }

    @Test
    fun deletingAPageRescuesBuiltIns() {
        val l = HomeLayout(pages = listOf(HomePage(id = "p0"), HomePage(id = "p1", items = listOf(AppItem(appKey = flyer)))))
        assertSame("refused without a config to place them", l, WorkspaceEngine.removePage(l, 1))
        val out = WorkspaceEngine.removePage(l, 1, config)
        assertEquals(1, out.pages.size)
        assertTrue(flyer in WorkspaceEngine.placedAppKeys(out))
    }

    @Test
    fun validatorAlwaysKeepsFlyerAndNeverHidesBuiltIns() {
        val l = HomeLayout(pages = listOf(HomePage(items = listOf(AppItem(appKey = "x")))), hiddenApps = setOf(flyer, "builtin:editor", "y"), initialized = true)
        val fixed = LayoutValidator.sanitize(l, config)
        assertTrue(flyer in WorkspaceEngine.placedAppKeys(fixed))
        assertEquals(setOf("y"), fixed.hiddenApps)
    }

    @Test
    fun styleAndLockSectionsRoundTripAndValidate() {
        val c = FlyerConfig(
            style = StyleConfig(material = SurfaceMaterial.LIQUID_GLASS, refraction = 0.9f, lightAngle = -90, iconPlates = true),
            lock = LockConfig(showButton = true, unlockMethod = UnlockMethod.LAUNCHER_PIN, holdDurationMs = 99_999, protectedApps = setOf("a/b@0", flyer), awayMessage = "x".repeat(500)),
        )
        val decoded = DocumentCodec.decodeDocument(DocumentCodec.encodeDocument(LauncherDocument(config = c))).value.config
        assertEquals(SurfaceMaterial.LIQUID_GLASS, decoded.style.material)
        assertEquals(270, decoded.style.lightAngle)
        assertTrue(decoded.style.iconPlates)
        assertEquals(UnlockMethod.LAUNCHER_PIN, decoded.lock.unlockMethod)
        assertEquals(4000, decoded.lock.holdDurationMs)
        assertEquals(setOf("a/b@0"), decoded.lock.protectedApps)
        assertEquals(120, decoded.lock.awayMessage.length)
        // The launcher PIN itself is never part of the config (or of backups).
        assertFalse(DocumentCodec.encodeBackup(LauncherDocument(config = c), com.flyer.launcher.core.persistence.BackupKind.FULL, "b", 0).contains("hash"))
    }

    @Test
    fun advancedThemesSetMaterialAndBackgroundButKeepArrangementAndLock() {
        val mine = FlyerConfig(home = HomeConfig(columns = 6, rows = 7), lock = LockConfig(showButton = true))
        ThemePresets.advanced.forEach { preset ->
            val t = ConfigValidator.sanitize(preset.apply(mine))
            assertTrue(preset.id, t.style.material != SurfaceMaterial.FLAT)
            assertTrue(preset.id, t.home.background.type != BackgroundType.SYSTEM_WALLPAPER)
            assertEquals(6, t.home.columns)
            assertEquals(7, t.home.rows)
            assertEquals(mine.lock, t.lock)
            assertEquals(mine.gestures, t.gestures)
        }
        // Classic themes go back to the flat material.
        val classic = ThemePresets.classic.first().apply(ThemePresets.advanced.first().apply(mine))
        assertEquals(SurfaceMaterial.FLAT, classic.style.material)
    }
}
