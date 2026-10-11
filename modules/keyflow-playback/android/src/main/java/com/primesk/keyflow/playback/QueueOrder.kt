package com.primesk.keyflow.playback

import kotlin.random.Random

/** IDs describe queue occurrences, never song identity. */
internal class QueueOrder {
  var base: List<String> = emptyList(); private set
  var displayed: List<String> = emptyList(); private set
  var shuffled = false; private set
  var revision = 0L; private set
  var priority: List<String> = emptyList(); private set
  fun replace(ids: List<String>) {
    require(ids.distinct().size == ids.size)
    base = ids.toList(); displayed = base; shuffled = false; priority = emptyList(); revision++
  }
  fun restore(baseIds: List<String>, displayIds: List<String>, shuffle: Boolean, pending: List<String> = emptyList()) {
    require(baseIds.distinct().size == baseIds.size && baseIds.toSet() == displayIds.toSet() && baseIds.size == displayIds.size)
    require(pending.distinct().size == pending.size && baseIds.containsAll(pending))
    base = baseIds.toList(); displayed = displayIds.toList(); shuffled = shuffle; priority = pending.toList(); revision++
  }
  fun shuffle(enabled: Boolean, current: String?, random: Random = Random.Default) {
    if (enabled == shuffled) return
    val pending = priority.filter { it != current }
    val pendingSet = pending.toSet()
    displayed = if (enabled) (if (current in base) listOf(current!!) else emptyList()) + pending +
      base.filter { it != current && it !in pendingSet }.shuffled(random) else base
    shuffled = enabled; revision++
  }
  fun insert(ids: List<String>, current: String?, playNext: Boolean): Int {
    require(ids.distinct().size == ids.size && ids.none { it in base })
    val at = if (playNext) insertionPoint(base, current, priority) else base.size
    base = base.toMutableList().apply { addAll(at, ids) }
    val displayAt = if (playNext) insertionPoint(displayed, current, priority) else displayed.size
    displayed = displayed.toMutableList().apply { addAll(displayAt, ids) }
    if (playNext) priority = priority + ids
    revision++; return at
  }
  fun remove(ids: Set<String>) {
    if (ids.none { it in base }) return
    base = base.filterNot(ids::contains); displayed = displayed.filterNot(ids::contains)
    priority = priority.filterNot(ids::contains); revision++
  }
  fun move(id: String, delta: Int) {
    require(id in displayed) { "This queue entry is no longer available." }
    val from = displayed.indexOf(id); val to = (from + delta).coerceIn(0, displayed.lastIndex)
    if (from == to) return
    displayed = displayed.toMutableList().apply { removeAt(from); add(to, id) }
    if (!shuffled) base = displayed
    priority = priority.filterNot { it == id }; revision++
  }
  fun playNext(id: String, current: String?) {
    require(id in base) { "This queue entry is no longer available." }
    if (id == current) return
    val pending = priority.filterNot { it == id }
    fun afterCurrent(values: List<String>) = values.filterNot { it == id }.toMutableList().apply {
      add(insertionPoint(this, current, pending), id)
    }.toList()
    base = afterCurrent(base); displayed = afterCurrent(displayed)
    priority = pending + id; revision++
  }
  private fun insertionPoint(values: List<String>, current: String?, pending: List<String>): Int {
    val afterCurrent = values.indexOf(current).let { if (it < 0) values.size else it + 1 }
    return maxOf(afterCurrent, pending.map(values::indexOf).maxOrNull()?.plus(1) ?: 0).coerceAtMost(values.size)
  }
  fun consume(id: String?) {
    if (id in priority) { priority = priority.filterNot { it == id }; revision++ }
  }
  companion object {
    fun restartPrevious(positionMs: Long) = positionMs > 10_000
  }
}
