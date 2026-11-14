package com.flyer.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import com.flyer.launcher.core.apps.AppSorting
import com.flyer.launcher.core.config.CellSizing
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.HomeConfig
import com.flyer.launcher.core.config.VerticalPlacement
import com.flyer.launcher.core.history.History
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.theme.ThemePresets
import com.flyer.launcher.core.widgets.WidgetEngine
import com.flyer.launcher.flyer.FlyerCategory
import com.flyer.launcher.flyer.settings.SettingsRegistry
import com.flyer.launcher.flyer.settings.SliderSpec
import com.flyer.launcher.launcher.home.ResizeEdge
import com.flyer.launcher.launcher.home.resizeCell
import com.flyer.launcher.launcher.render.GridGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscLogicTest {

    @Test
    fun historyUndoRedoAndCoalescing() {
        var now = 0L
        val h = History(0, capacity = 3) { now }
        h.push(1, "one", "slider"); now += 100
        h.push(2, "two", "slider"); now += 100 // coalesced with "one"
        h.push(3, "three")
        assertEquals(3, h.current)
        assertEquals(2, h.undo())
        assertEquals(0, h.undo())
        assertFalse(h.canUndo)
        assertEquals(2, h.redo())
        h.push(9, "nine")
        assertFalse(h.canRedo)
        repeat(5) { h.push(10 + it, "n$it") }
        var undos = 0
        while (h.undo() != null) undos++
        assertEquals(3, undos)
    }

    @Test
    fun fuzzyScoringPrefersPrefixAndInitials() {
        val prefix = AppSorting.score("Gmail", "gm", true)
        val initials = AppSorting.score("Google Maps", "gm", true)
        val fuzzy = AppSorting.score("Game Center Manager", "gcm", true)
        assertTrue(prefix > initials)
        assertTrue(initials > 0)
        assertTrue(fuzzy > 0)
        assertEquals(0, AppSorting.score("Camera", "xyz", true))
        assertEquals(0, AppSorting.score("Camera", "cmr", false))
        assertTrue(AppSorting.score("Ćamera", "cam", false) > 0)
        assertEquals('#', AppSorting.indexLetter("1Password"))
        assertEquals('E', AppSorting.indexLetter("éclair"))
    }

    @Test
    fun gridGeometryMapsCellsAndPointsConsistently() {
        val home = HomeConfig(columns = 4, rows = 5, marginStartDp = 10, marginEndDp = 10, marginTopDp = 0, marginBottomDp = 0, cellSpacingHDp = 0, cellSpacingVDp = 0)
        val g = GridGeometry.compute(420f, 500f, 4, 5, home, Density(1f), freePositioning = false)
        assertEquals(100f, g.cellWidth, 0.01f)
        assertEquals(100f, g.cellHeight, 0.01f)
        val r = g.cellRect(Cell(2, 3, 2, 1))
        assertEquals(210f, r.left, 0.01f)
        assertEquals(300f, r.top, 0.01f)
        assertEquals(200f, r.width, 0.01f)
        assertEquals(2 to 3, g.cellAt(Offset(250f, 350f)))
        assertEquals(Cell(2, 3), g.cellForTopLeft(Offset(240f, 330f), 1, 1))
        val free = g.freeFor(Offset(110f, 250f), 1, 1, snapPx = 0f)
        assertEquals(0.25f, free.x, 0.001f)
        assertEquals(0.5f, free.y, 0.001f)
    }

    @Test
    fun fixedCellHeightIsPlacedAccordingToAlignment() {
        val home = HomeConfig(columns = 4, rows = 2, marginStartDp = 0, marginEndDp = 0, marginTopDp = 0, marginBottomDp = 0, cellSizing = CellSizing.FIXED, fixedCellHeightDp = 100, gridPlacement = VerticalPlacement.BOTTOM)
        val g = GridGeometry.compute(400f, 1000f, 4, 2, home, Density(1f), false)
        assertEquals(800f, g.top, 0.01f)
        assertEquals(100f, g.cellHeight, 0.01f)
    }

    @Test
    fun resizeRespectsLimitsAndGrid() {
        val limits = WidgetEngine.SpanLimits(defaultX = 2, defaultY = 2, minX = 2, minY = 1, maxX = 3, maxY = 4)
        val cell = Cell(1, 1, 2, 2)
        assertEquals(Cell(1, 1, 3, 2), resizeCell(cell, ResizeEdge.RIGHT, 5, limits, 4, 4, true))
        assertEquals(Cell(1, 1, 2, 2), resizeCell(cell, ResizeEdge.RIGHT, -3, limits, 4, 4, true))
        assertEquals(Cell(0, 1, 3, 2), resizeCell(cell, ResizeEdge.LEFT, 1 * -1, limits, 4, 4, true))
        assertEquals(Cell(1, 2, 2, 1), resizeCell(cell, ResizeEdge.TOP, 1, limits, 4, 4, true))
        assertEquals(Cell(1, 1, 3, 2), resizeCell(cell, ResizeEdge.RIGHT, 9, null, 4, 4, false))
    }

    @Test
    fun settingsRegistryIsConsistent() {
        val ids = SettingsRegistry.all.map { it.id }
        assertEquals("setting ids must be unique", ids.size, ids.toSet().size)
        val defaults = FlyerConfig()
        SettingsRegistry.all.forEach { spec ->
            assertTrue("${spec.id} should be default on a fresh config", spec.isDefault(defaults))
            assertEquals("${spec.id} reset must be identity on defaults", defaults, spec.reset(defaults))
        }
        // Every slider default lies within its range; changing a slider actually changes the config.
        SettingsRegistry.all.filterIsInstance<SliderSpec>().forEach { s ->
            val v = s.get(defaults)
            assertTrue("${s.id} default $v outside ${s.range}", v in s.range)
            val other = if (v == s.range.endInclusive) s.range.start else s.range.endInclusive
            assertNotEquals("${s.id} setter has no effect", defaults, s.set(defaults, other))
        }
        // Every category that has generated settings is searchable.
        FlyerCategory.entries.forEach { c ->
            SettingsRegistry.forCategory(c).firstOrNull()?.let { spec -> assertTrue(SettingsRegistry.search(spec.title, defaults).any { it.spec.id == spec.id }) }
        }
        assertTrue(SettingsRegistry.all.size > 150)
    }

    @Test
    fun themesOnlyChangeVisualsAndKeepArrangementSettings() {
        val mine = FlyerConfig().let { c -> c.copy(home = c.home.copy(columns = 6, rows = 7), dock = c.dock.copy(slots = 4)) }
        ThemePresets.all.forEach { preset ->
            val themed = preset.apply(mine)
            assertEquals(6, themed.home.columns)
            assertEquals(7, themed.home.rows)
            assertEquals(4, themed.dock.slots)
            assertEquals(mine.gestures, themed.gestures)
            assertEquals(mine.advanced, themed.advanced)
        }
        assertEquals(ThemePresets.all.size, ThemePresets.all.map { it.id }.toSet().size)
    }
}
