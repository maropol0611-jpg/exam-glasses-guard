package com.classroom.rfdetector.core

/** BLE 스캐너가 건네는, 키트 형식으로 보이는 광고 한 건. 서명은 아직 확인하지 않았다. */
class KitSighting(val advertisement: ByteArray, val rssi: Int, val observedAtMs: Long)

/** 서명이 맞는 키트 판정 한 건. */
data class KitEvent(val id: Long, val message: KitMessage, val rssi: Int, val receivedAtMs: Long)

/**
 * 화면에 보여 줄 키트 상태.
 * unreadable: 키트 형식인데 읽지 못한 신호의 수(키트 코드가 없거나, 서명이 맞지 않음). 같은 신호는 5초에 한 번만 센다.
 */
data class KitStatus(val keySet: Boolean = false, val unreadable: Int = 0, val events: List<KitEvent> = emptyList()) {
    val checkCount: Int get() = events.count { it.message.verdict == KitVerdict.CHECK }
}

/**
 * 키트 판정 모음. 안드로이드와 무관한 순수 코드라 단위 테스트로 확인한다.
 * 저장하지 않는다. 앱을 닫거나 새 측정을 시작하면 사라지고, 키트 코드도 메모리에만 둔다.
 */
class KitLog(private val maxEvents: Int = 50) {
    private var key: ByteArray? = null
    private var replay = KitReplayFilter()
    private var nextId = 1L
    private var events = emptyList<KitEvent>()   // 최신이 앞
    private var unreadable = 0
    private var lastUnreadableAtMs: Long? = null

    /** 빈 문자열이면 키트 수신을 끈다. 코드를 바꾸면 반복 광고 기록은 새로 시작한다. */
    @Synchronized fun setCode(code: String) {
        key = code.trim().takeIf { it.isNotEmpty() }?.let { KitProtocol.deriveKey(it) }
        replay = KitReplayFilter()
        unreadable = 0
        lastUnreadableAtMs = null
    }

    /** 새 측정을 시작할 때 판정 목록을 비운다. 키트 코드는 유지한다. */
    @Synchronized fun reset() {
        events = emptyList()
        replay = KitReplayFilter()
        unreadable = 0
        lastUnreadableAtMs = null
    }

    /** 새 판정이 목록에 올랐으면 true. 같은 메시지의 반복 광고와 재전송은 false 다. */
    @Synchronized fun accept(sighting: KitSighting): Boolean {
        val message = key?.let { KitProtocol.parseAd(sighting.advertisement, it) }
        if (message == null) {
            val last = lastUnreadableAtMs
            if (last == null || sighting.observedAtMs - last >= UNREADABLE_GAP_MS) {
                unreadable++
                lastUnreadableAtMs = sighting.observedAtMs
            }
            return false
        }
        if (!replay.isNew(message)) return false
        events = (listOf(KitEvent(nextId++, message, sighting.rssi, sighting.observedAtMs)) + events).take(maxEvents)
        return true
    }

    @Synchronized fun dismiss(id: Long) { events = events.filterNot { it.id == id } }

    @Synchronized fun status() = KitStatus(key != null, unreadable, events)

    private companion object { const val UNREADABLE_GAP_MS = 5_000L }
}
