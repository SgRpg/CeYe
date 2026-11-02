package io.github.sgrpg.ceye

fun <T> selectionRange(ids: List<T>, baseline: Set<T>, anchor: T, end: T, add: Boolean): Set<T> {
    val a = ids.indexOf(anchor); val b = ids.indexOf(end)
    if (a < 0 || b < 0) return baseline
    val range = ids.subList(minOf(a, b), maxOf(a, b) + 1).toSet()
    return if (add) baseline + range else baseline - range
}
