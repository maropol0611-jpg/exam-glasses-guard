package com.classroom.rfdetector.core

data class ObservationPeriod(val firstSeenMs: Long, val lastSeenMs: Long, val count: Int)

fun ScanSource.historyGapMs(): Long = if (this == ScanSource.WIFI) 90_000L else 15_000L

fun signalPresence(source: ScanSource, lastSeenMs: Long, nowMs: Long): SignalPresence {
    val age = (nowMs - lastSeenMs).coerceAtLeast(0)
    return when {
        age <= if (source == ScanSource.WIFI) 45_000 else 5_000 -> SignalPresence.RECENT
        age <= source.historyGapMs() -> SignalPresence.QUIET
        else -> SignalPresence.HISTORY
    }
}

/** Merge late samples too: a delayed packet may bridge two apparent gaps. */
fun addToPeriods(periods: List<ObservationPeriod>, timeMs: Long, gapMs: Long): List<ObservationPeriod> {
    val sorted = (periods + ObservationPeriod(timeMs, timeMs, 1)).sortedBy { it.firstSeenMs }
    val merged = mutableListOf<ObservationPeriod>()
    for (period in sorted) {
        val previous = merged.lastOrNull()
        if (previous != null && period.firstSeenMs - previous.lastSeenMs <= gapMs) {
            merged[merged.lastIndex] = ObservationPeriod(previous.firstSeenMs, maxOf(previous.lastSeenMs, period.lastSeenMs), previous.count + period.count)
        } else merged.add(period)
    }
    return merged
}
