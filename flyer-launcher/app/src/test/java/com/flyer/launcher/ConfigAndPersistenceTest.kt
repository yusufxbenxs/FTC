package com.flyer.launcher

import com.flyer.launcher.core.config.ActionType
import com.flyer.launcher.core.config.BackgroundType
import com.flyer.launcher.core.config.ColorValue
import com.flyer.launcher.core.config.ConfigValidator
import com.flyer.launcher.core.config.DockConfig
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.GestureAction
import com.flyer.launcher.core.config.GestureType
import com.flyer.launcher.core.config.HomeConfig
import com.flyer.launcher.core.config.IconConfig
import com.flyer.launcher.core.config.IconShape
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.model.HomePage
import com.flyer.launcher.core.model.IconSource
import com.flyer.launcher.core.model.AppOverride
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.persistence.BackupKind
import com.flyer.launcher.core.persistence.ConfigMigrations
import com.flyer.launcher.core.persistence.DocumentCodec
import com.flyer.launcher.core.persistence.InvalidBackupException
import com.flyer.launcher.core.persistence.LauncherDocument
import com.flyer.launcher.core.workspace.LayoutValidator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConfigAndPersistenceTest {

    @After
    fun tearDown() {
        ConfigMigrations.testSteps = null
    }

    @Test
    fun colorValueRoundTripsThroughHex() {
        val c = ColorValue(0x80FF7AD9.toInt())
        assertEquals("#80FF7AD9", c.toHex())
        assertEquals(c, ColorValue.parse("#80ff7ad9"))
        assertEquals(ColorValue(0xFFAABBCC.toInt()), ColorValue.parse("AABBCC"))
        assertEquals(ColorValue(0xFFAABBCC.toInt()), ColorValue.parse("#abc"))
        assertNull(ColorValue.parse("#12345"))
        assertNull(ColorValue.parse("zz;"))
        assertEquals(0.5f, ColorValue.White.withAlpha(0.5f).alpha, 0.01f)
    }

    @Test
    fun validatorClampsOutOfRangeValues() {
        val bad = FlyerConfig(
            home = HomeConfig(columns = 99, rows = -3, marginStartDp = 5000),
            icons = IconConfig(sizeDp = 4, opacity = Float.NaN, saturation = 7f),
            dock = DockConfig(slots = 0, lengthFraction = 3f),
        )
        val c = ConfigValidator.sanitize(bad)
        assertEquals(12, c.home.columns)
        assertEquals(2, c.home.rows)
        assertEquals(96, c.home.marginStartDp)
        assertEquals(24, c.icons.sizeDp)
        assertEquals(1f, c.icons.opacity, 0f)
        assertEquals(1f, c.icons.saturation, 0f)
        assertEquals(2, c.dock.slots)
        assertEquals(1f, c.dock.lengthFraction, 0f)
    }

    @Test
    fun validatorKeepsFlyerReachable() {
        val c = FlyerConfig().let { it.copy(gestures = it.gestures.copy(bindings = mapOf(GestureType.LONG_PRESS_EMPTY to GestureAction(ActionType.NONE)))) }
        val s = ConfigValidator.sanitize(c)
        assertEquals(ActionType.HOME_MENU, s.gestures.bindings[GestureType.LONG_PRESS_EMPTY]?.type)
        // Missing bindings are restored from the defaults.
        assertEquals(GestureType.entries.size, s.gestures.bindings.size)
    }

    @Test
    fun validatorRejectsImageBackgroundWithoutImage() {
        val c = FlyerConfig().let { it.copy(home = it.home.copy(background = it.home.background.copy(type = BackgroundType.IMAGE, imagePath = null))) }
        assertEquals(BackgroundType.SYSTEM_WALLPAPER, ConfigValidator.sanitize(c).home.background.type)
    }

    @Test
    fun invalidDatePatternFallsBackToDefault() {
        val c = FlyerConfig().let { it.copy(widgets = it.widgets.copy(clock = it.widgets.clock.copy(datePattern = "'unterminated"))) }
        assertEquals(FlyerConfig().widgets.clock.datePattern, ConfigValidator.sanitize(c).widgets.clock.datePattern)
    }

    @Test
    fun documentRoundTrip() {
        val doc = LauncherDocument(
            config = FlyerConfig(icons = IconConfig(shape = IconShape.HEXAGON, sizeDp = 64)),
            layout = HomeLayout(
                pages = listOf(HomePage(items = listOf(AppItem(id = "a", appKey = "com.x/.Main@0", cell = Cell(1, 1)), FolderItem(id = "f", apps = listOf("a", "b"), cell = Cell(2, 2))))),
                appOverrides = mapOf("com.x/.Main@0" to AppOverride(label = "X", icon = IconSource.Pack("pack", "ic_x"))),
                initialized = true,
            ),
        )
        val decoded = DocumentCodec.decodeDocument(DocumentCodec.encodeDocument(doc))
        assertTrue(decoded.warnings.isEmpty())
        assertEquals(doc.config, decoded.value.config)
        assertEquals(doc.layout, decoded.value.layout)
    }

    @Test
    fun corruptSectionOnlyResetsThatSection() {
        val json = DocumentCodec.encodeDocument(LauncherDocument(config = FlyerConfig(icons = IconConfig(sizeDp = 70))))
            .replace("\"slots\":5", "\"slots\":\"not a number\"")
        assertTrue(json.contains("\"slots\":\"not a number\""))
        val decoded = DocumentCodec.decodeDocument(json)
        assertEquals(70, decoded.value.config.icons.sizeDp)
        assertEquals(DockConfig(), decoded.value.config.dock)
        assertTrue(decoded.warnings.any { "dock" in it })
    }

    @Test
    fun unknownEnumValuesAndKeysAreTolerated() {
        val json = DocumentCodec.encodeDocument(LauncherDocument())
            .replace("\"shape\":\"SQUIRCLE\"", "\"shape\":\"STAR_FROM_THE_FUTURE\"")
            .replaceFirst("{", "{\"someFutureField\":42,")
        val decoded = DocumentCodec.decodeDocument(json)
        assertEquals(IconConfig().shape, decoded.value.config.icons.shape)
    }

    @Test
    fun backupRoundTripAndThemeOmitsLayout() {
        val doc = LauncherDocument(layout = HomeLayout(pages = listOf(HomePage(items = listOf(AppItem(id = "a", appKey = "k")))), initialized = true))
        val full = DocumentCodec.decodeBackup(DocumentCodec.encodeBackup(doc, BackupKind.FULL, "Mine", 1L))
        assertEquals(BackupKind.FULL, full.kind)
        assertEquals("Mine", full.name)
        assertEquals(1, full.document.layout.pages[0].items.size)
        val theme = DocumentCodec.decodeBackup(DocumentCodec.encodeBackup(doc, BackupKind.THEME, "Look", 1L))
        assertEquals(BackupKind.THEME, theme.kind)
        assertTrue(theme.document.layout.pages[0].items.isEmpty())
    }

    @Test
    fun nonBackupFilesAreRejected() {
        try {
            DocumentCodec.decodeBackup("{\"hello\":1}")
            fail("should throw")
        } catch (_: InvalidBackupException) {
        }
        try {
            DocumentCodec.decodeBackup("not json at all")
            fail("should throw")
        } catch (_: InvalidBackupException) {
        }
    }

    @Test
    fun migrationsRunInOrder() {
        ConfigMigrations.testSteps = mapOf(
            1 to ConfigMigrations.Migration { JsonObject(it + ("step1" to JsonPrimitive(true))) },
            2 to ConfigMigrations.Migration { JsonObject(it + ("step2" to JsonPrimitive(true))) },
        )
        val out = ConfigMigrations.migrate(JsonObject(emptyMap()), 1, mutableListOf())
        assertEquals(JsonPrimitive(true), out["step1"])
        assertEquals(JsonPrimitive(true), out["step2"])
        assertEquals(JsonPrimitive(3), out["schemaVersion"])
    }

    @Test
    fun layoutValidatorRepairsOverlapsDuplicatesAndDockOverflow() {
        val config = FlyerConfig(home = HomeConfig(columns = 4, rows = 4), dock = DockConfig(slots = 2))
        val layout = HomeLayout(
            pages = listOf(
                HomePage(
                    items = listOf(
                        AppItem(id = "same", appKey = "a", cell = Cell(0, 0)),
                        AppItem(id = "same", appKey = "b", cell = Cell(0, 0)),
                        AppItem(id = "c", appKey = "c", cell = Cell(9, 9)),
                        FolderItem(id = "solo", apps = listOf("only")),
                        WidgetItem(id = "w", appWidgetId = 1, provider = "p/q", cell = Cell(3, 3, 6, 6)),
                    ),
                ),
            ),
            dock = listOf(AppItem(id = "d1", appKey = "1", cell = Cell(5, 0)), AppItem(id = "d2", appKey = "2", cell = Cell(1, 0)), AppItem(id = "d3", appKey = "3", cell = Cell(2, 0))),
        )
        val fixed = LayoutValidator.sanitize(layout, config)
        val all = fixed.pages.flatMap { it.items }
        assertEquals(all.size, all.map { it.id }.toSet().size)
        val onFirst = fixed.pages[0].items
        for (i in onFirst.indices) for (j in i + 1 until onFirst.size) assertFalse("overlap ${onFirst[i]} ${onFirst[j]}", onFirst[i].cell.overlaps(onFirst[j].cell))
        assertTrue(all.all { it.cell.fitsIn(4, 4) })
        assertTrue(all.any { it is AppItem && it.appKey == "only" })
        assertEquals(2, fixed.dock.size)
        assertEquals(listOf("2", "3"), fixed.dock.map { (it as AppItem).appKey })
        assertTrue(all.any { it is AppItem && it.appKey == "1" })
    }
}
