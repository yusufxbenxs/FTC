package com.flyer.launcher

import com.flyer.launcher.core.config.DockConfig
import com.flyer.launcher.core.config.FlyerConfig
import com.flyer.launcher.core.config.HomeConfig
import com.flyer.launcher.core.config.Positioning
import com.flyer.launcher.core.model.AppItem
import com.flyer.launcher.core.model.Cell
import com.flyer.launcher.core.model.FolderItem
import com.flyer.launcher.core.model.FreePos
import com.flyer.launcher.core.model.HomeLayout
import com.flyer.launcher.core.model.HomePage
import com.flyer.launcher.core.model.Surface
import com.flyer.launcher.core.model.WidgetItem
import com.flyer.launcher.core.workspace.DropTarget
import com.flyer.launcher.core.workspace.GridDims
import com.flyer.launcher.core.workspace.Placement
import com.flyer.launcher.core.workspace.WorkspaceEngine
import com.flyer.launcher.core.workspace.WorkspaceEngine.DropResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceEngineTest {

    private val config = FlyerConfig(home = HomeConfig(columns = 4, rows = 4), dock = DockConfig(slots = 3))

    private fun layout(vararg items: AppItem, dock: List<AppItem> = emptyList()) =
        HomeLayout(pages = listOf(HomePage(id = "p0", items = items.toList())), dock = dock, initialized = true)

    private fun accepted(r: DropResult): HomeLayout {
        assertTrue("expected accepted but was $r", r is DropResult.Accepted)
        return (r as DropResult.Accepted).layout
    }

    @Test
    fun findFreeCellReturnsFirstFreeInReadingOrder() {
        val items = listOf(AppItem(id = "a", appKey = "a", cell = Cell(0, 0)), AppItem(id = "b", appKey = "b", cell = Cell(1, 0)))
        assertEquals(Cell(2, 0), WorkspaceEngine.findFreeCell(items, GridDims(4, 4), 1, 1))
    }

    @Test
    fun findFreeCellHonorsSpansAndReturnsNullWhenFull() {
        val items = (0 until 4).flatMap { y -> (0 until 4).map { x -> AppItem(id = "$x$y", appKey = "k$x$y", cell = Cell(x, y)) } }
        assertNull(WorkspaceEngine.findFreeCell(items, GridDims(4, 4), 1, 1))
        val half = items.filter { it.cell.y < 2 }
        assertEquals(Cell(0, 2, 2, 2), WorkspaceEngine.findFreeCell(half, GridDims(4, 4), 2, 2))
    }

    @Test
    fun dropOnFreeCellMovesItem() {
        val l = layout(AppItem(id = "a", appKey = "app.a", cell = Cell(0, 0)))
        val out = accepted(WorkspaceEngine.drop(l, Placement.Existing("a"), DropTarget.AtCell(Surface.Page(0), 3, 2), config))
        assertEquals(Cell(3, 2), out.pages[0].items.single().cell)
    }

    @Test
    fun dropOnOccupiedCellUsesNearestFreeCell() {
        val l = layout(AppItem(id = "a", appKey = "app.a", cell = Cell(0, 0)), AppItem(id = "b", appKey = "app.b", cell = Cell(1, 1)))
        val out = accepted(WorkspaceEngine.drop(l, Placement.Existing("a"), DropTarget.AtCell(Surface.Page(0), 1, 1), config))
        val moved = out.pages[0].items.first { it.id == "a" }
        assertTrue(moved.cell != Cell(1, 1))
        val dist = kotlin.math.abs(moved.cell.x - 1) + kotlin.math.abs(moved.cell.y - 1)
        assertEquals(1, dist)
    }

    @Test
    fun dropAppOntoAppCreatesFolderAtTargetPosition() {
        val l = layout(AppItem(id = "a", appKey = "app.a", cell = Cell(0, 0)), AppItem(id = "b", appKey = "app.b", cell = Cell(2, 3)))
        val out = accepted(WorkspaceEngine.drop(l, Placement.Existing("a"), DropTarget.OntoItem(Surface.Page(0), "b"), config))
        val folder = out.pages[0].items.single() as FolderItem
        assertEquals(listOf("app.b", "app.a"), folder.apps)
        assertEquals(Cell(2, 3), folder.cell)
    }

    @Test
    fun dropAppOntoFolderAddsIt() {
        val folder = FolderItem(id = "f", name = "F", apps = listOf("x", "y"), cell = Cell(1, 1))
        val l = HomeLayout(pages = listOf(HomePage(items = listOf(folder, AppItem(id = "a", appKey = "z", cell = Cell(0, 0))))))
        val out = accepted(WorkspaceEngine.drop(l, Placement.Existing("a"), DropTarget.OntoItem(Surface.Page(0), "f"), config))
        assertEquals(listOf("x", "y", "z"), (out.pages[0].items.single() as FolderItem).apps)
    }

    @Test
    fun removingAppFromTwoAppFolderUnwrapsFolder() {
        val folder = FolderItem(id = "f", name = "F", apps = listOf("x", "y"), cell = Cell(1, 1))
        val l = HomeLayout(pages = listOf(HomePage(items = listOf(folder))))
        val out = accepted(WorkspaceEngine.drop(l, Placement.FromFolder("f", "y"), DropTarget.AtCell(Surface.Page(0), 3, 3), config))
        val items = out.pages[0].items
        assertEquals(2, items.size)
        assertTrue(items.any { it is AppItem && it.appKey == "x" && it.cell == Cell(1, 1) })
        assertTrue(items.any { it is AppItem && it.appKey == "y" && it.cell == Cell(3, 3) })
    }

    @Test
    fun dockInsertRespectsSlotLimitAndOrder() {
        val dock = listOf(AppItem(id = "d1", appKey = "1", cell = Cell(0, 0)), AppItem(id = "d2", appKey = "2", cell = Cell(1, 0)))
        val l = layout(AppItem(id = "a", appKey = "new", cell = Cell(0, 0)), dock = dock)
        val out = accepted(WorkspaceEngine.drop(l, Placement.Existing("a"), DropTarget.DockIndex(1), config))
        assertEquals(listOf("1", "new", "2"), out.dock.sortedBy { it.cell.x }.map { (it as AppItem).appKey })
        assertEquals(listOf(0, 1, 2), out.dock.map { it.cell.x }.sorted())
        val full = WorkspaceEngine.drop(out, Placement.NewApp("more"), DropTarget.DockIndex(0), config)
        assertTrue(full is DropResult.Rejected)
    }

    @Test
    fun widgetsCannotGoInTheDock() {
        val w = WidgetItem(id = "w", appWidgetId = 5, provider = "p/q", cell = Cell(0, 0, 2, 2))
        val l = HomeLayout(pages = listOf(HomePage(items = listOf(w))))
        assertTrue(WorkspaceEngine.drop(l, Placement.Existing("w"), DropTarget.DockIndex(0), config) is DropResult.Rejected)
    }

    @Test
    fun dropOnMissingPageCreatesIt() {
        val l = layout(AppItem(id = "a", appKey = "app.a", cell = Cell(0, 0)))
        val out = accepted(WorkspaceEngine.drop(l, Placement.Existing("a"), DropTarget.AtCell(Surface.Page(1), 0, 0), config))
        assertEquals(2, out.pages.size)
        assertTrue(out.pages[0].items.isEmpty())
        assertEquals("a", out.pages[1].items.single().id)
    }

    @Test
    fun freeDropStoresFractionalPosition() {
        val free = config.copy(home = config.home.copy(positioning = Positioning.FREE, allowOverlap = true))
        val l = layout(AppItem(id = "a", appKey = "app.a", cell = Cell(0, 0)))
        val out = accepted(WorkspaceEngine.drop(l, Placement.Existing("a"), DropTarget.AtFree(Surface.Page(0), FreePos(0.33f, 0.5f), Cell(1, 2)), free))
        assertEquals(FreePos(0.33f, 0.5f), out.pages[0].items.single().free)
    }

    @Test
    fun addAppCreatesPageWhenAllPagesFull() {
        val full = (0 until 4).flatMap { y -> (0 until 4).map { x -> AppItem(id = "$x$y", appKey = "k$x$y", cell = Cell(x, y)) } }
        val l = HomeLayout(pages = listOf(HomePage(items = full)))
        val out = WorkspaceEngine.addApp(l, "extra", config)
        assertEquals(2, out.pages.size)
        assertEquals("extra", (out.pages[1].items.single() as AppItem).appKey)
    }

    @Test
    fun removeAppCleansEverySurface() {
        val l = HomeLayout(
            pages = listOf(HomePage(items = listOf(AppItem(id = "a", appKey = "gone"), FolderItem(id = "f", apps = listOf("gone", "x", "y"))))),
            dock = listOf(AppItem(id = "d", appKey = "gone")),
        )
        val out = WorkspaceEngine.removeApp(l, "gone")
        assertNull(out.pages[0].items.firstOrNull { it is AppItem })
        assertEquals(listOf("x", "y"), (out.pages[0].items.single() as FolderItem).apps)
        assertTrue(out.dock.isEmpty())
        assertTrue("gone" !in WorkspaceEngine.placedAppKeys(out))
    }

    @Test
    fun pageOperations() {
        var l = HomeLayout(pages = listOf(HomePage(id = "a"), HomePage(id = "b")))
        l = WorkspaceEngine.addPage(l, 1)
        assertEquals(3, l.pages.size)
        l = WorkspaceEngine.movePage(l, 0, 2)
        assertEquals("a", l.pages[2].id)
        l = WorkspaceEngine.removePage(l, 0)
        assertEquals(2, l.pages.size)
        val single = HomeLayout(pages = listOf(HomePage()))
        assertEquals(1, WorkspaceEngine.removePage(single, 0).pages.size)
    }

    @Test
    fun autoArrangePacksItemsInReadingOrder() {
        val items = listOf(AppItem(id = "b", appKey = "b", cell = Cell(5, 3)), AppItem(id = "a", appKey = "a", cell = Cell(2, 0)))
        val arranged = WorkspaceEngine.autoArrange(items, GridDims(4, 4))
        assertEquals(Cell(0, 0), arranged.first { it.id == "a" }.cell)
        assertEquals(Cell(1, 0), arranged.first { it.id == "b" }.cell)
        assertNotNull(arranged.all { it.free == null })
    }
}
