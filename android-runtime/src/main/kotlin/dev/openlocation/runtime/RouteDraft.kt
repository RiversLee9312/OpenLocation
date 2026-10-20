package dev.openlocation.runtime

import dev.openlocation.scenario.GeoPoint

/** Transactional editor history. The active simulation is changed only by an explicit save. */
class RouteDraft(initial: List<GeoPoint>) {
    var points: List<GeoPoint> = initial.toList()
        private set

    private val past = ArrayDeque<List<GeoPoint>>()
    private val future = ArrayDeque<List<GeoPoint>>()
    val canUndo
        get() = past.isNotEmpty()

    val canRedo
        get() = future.isNotEmpty()

    private fun change(next: List<GeoPoint>) {
        require(next.size <= 10000)
        if (next == points) return
        past.addLast(points)
        if (past.size > 50) past.removeFirst()
        points = next
        future.clear()
    }

    fun insert(after: Int?, point: GeoPoint): Int {
        val index = after?.let { (it + 1).coerceIn(0, points.size) } ?: points.size
        change(points.toMutableList().apply { add(index, point) })
        return index
    }

    fun move(index: Int, point: GeoPoint) =
        change(points.toMutableList().apply { set(index, point) })

    fun delete(index: Int) = change(points.filterIndexed { i, _ -> i != index })

    fun clear() = change(emptyList())

    fun undo() {
        if (canUndo) {
            future.addLast(points)
            points = past.removeLast()
        }
    }

    fun redo() {
        if (canRedo) {
            past.addLast(points)
            points = future.removeLast()
        }
    }
}
