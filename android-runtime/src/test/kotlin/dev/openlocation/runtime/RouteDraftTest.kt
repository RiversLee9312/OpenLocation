package dev.openlocation.runtime

import dev.openlocation.scenario.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class RouteDraftTest {
    @Test
    fun editMiddlePointsAndRecoverHistory() {
        val original =
            listOf(GeoPoint(30.0, 120.0), GeoPoint(30.001, 120.0), GeoPoint(30.002, 120.0))
        val draft = RouteDraft(original)
        val replacement = GeoPoint(30.001, 120.001)
        draft.move(1, replacement)
        draft.delete(0)
        assertEquals(listOf(replacement, original[2]), draft.points)
        draft.undo()
        assertEquals(replacement, draft.points[1])
        draft.undo()
        assertEquals(original, draft.points)
        draft.redo()
        assertEquals(2, draft.insert(1, original[0]))
        assertFalse(draft.canRedo)
        assertEquals(original[0], draft.points[2])
        draft.clear()
        assertTrue(draft.points.isEmpty())
        draft.undo()
        assertEquals(4, draft.points.size)
    }
}
