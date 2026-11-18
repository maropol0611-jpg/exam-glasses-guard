package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class KitLogTest {
    private val code = "exam-2026-1118"
    private fun sighting(message: KitMessage, atMs: Long = 1_000L, key: ByteArray = KitProtocol.deriveKey(code), rssi: Int = -60) =
        KitSighting(KitProtocol.encodeAd(message, key), rssi, atMs)
    private val check = KitMessage(3, 42, KitVerdict.CHECK, 71, 17)

    @Test fun deriveKeyMatchesThePythonReferenceVector() {
        assertEquals("3de370634bf0eab78c241faf28327bf5", KitProtocol.deriveKey(code).joinToString("") { "%02x".format(it) })
        assertArrayEquals(KitProtocol.deriveKey(code), KitProtocol.deriveKey("  $code "))
    }

    @Test fun withoutACodeNothingIsReadAndTheSightingIsCountedOncePerFiveSeconds() {
        val log = KitLog()
        assertFalse(log.accept(sighting(check, atMs = 1_000)))
        assertFalse(log.accept(sighting(check, atMs = 2_000)))
        assertFalse(log.accept(sighting(check, atMs = 7_000)))
        val status = log.status()
        assertFalse(status.keySet)
        assertEquals(2, status.unreadable)
        assertTrue(status.events.isEmpty())
    }

    @Test fun signedMessageIsListedOnceAndRepeatedAdvertisingIsIgnored() {
        val log = KitLog().apply { setCode(code) }
        assertTrue(log.accept(sighting(check)))
        assertFalse(log.accept(sighting(check)))
        val event = log.status().events.single()
        assertEquals(check, event.message)
        assertEquals(1, log.status().checkCount)
        assertTrue(log.accept(sighting(check.copy(seq = 43, verdict = KitVerdict.PASS, seat = 18))))
        assertEquals(listOf(18, 17), log.status().events.map { it.message.seat })   // 최신이 앞
        assertEquals(1, log.status().checkCount)
    }

    @Test fun wrongKeyIsReportedAsUnreadableNotAsAJudgment() {
        val log = KitLog().apply { setCode("another-code") }
        assertFalse(log.accept(sighting(check)))
        assertEquals(1, log.status().unreadable)
        assertTrue(log.status().events.isEmpty())
    }

    @Test fun dismissResetAndCodeChangeBehave() {
        val log = KitLog().apply { setCode(code) }
        log.accept(sighting(check))
        log.dismiss(log.status().events.single().id)
        assertTrue(log.status().events.isEmpty())
        assertFalse(log.accept(sighting(check)))          // 빼도 같은 seq 의 반복 광고는 다시 올리지 않는다
        log.accept(sighting(check.copy(seq = 50)))
        log.reset()
        assertTrue(log.status().events.isEmpty())
        assertTrue(log.status().keySet)                    // 새 측정을 시작해도 코드는 유지
        assertTrue(log.accept(sighting(check)))            // 반복 광고 기록은 새로 시작
        log.setCode("")
        assertFalse(log.status().keySet)
    }

    @Test fun listKeepsOnlyTheNewestEntries() {
        val log = KitLog(maxEvents = 3).apply { setCode(code) }
        (1..5).forEach { log.accept(sighting(check.copy(seq = it))) }
        assertEquals(listOf(5, 4, 3), log.status().events.map { it.message.seq })
    }
}
