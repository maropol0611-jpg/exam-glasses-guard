package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

/** 기준 구현(software/kit_beacon.py)이 만든 시험 벡터와 같은 바이트를 만들고 읽는지 대조한다. */
class KitMessageTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private val key = hex("000102030405060708090a0b0c0d0e0f")
    private val message = KitMessage(kitId = 3, seq = 42, verdict = KitVerdict.CHECK, score = 71, seat = 17)
    private val vector = "02010613ffffff4b5301032a00014711003085155b9b7a"

    @Test fun encodingMatchesThePythonReferenceVector() {
        assertEquals(vector, KitProtocol.encodeAd(message, key).toHex())
    }

    @Test fun parsingTheReferenceVectorGivesTheSameFields() {
        assertEquals(message, KitProtocol.parseAd(hex(vector), key))
    }

    @Test fun wrongKeyAndTamperingAreRejected() {
        assertNull(KitProtocol.parseAd(hex(vector), ByteArray(16)))
        val flippedTag = hex(vector).also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertNull(KitProtocol.parseAd(flippedTag, key))
        val changedVerdict = hex(vector).also { it[13] = 0 }   // CHECK → PASS
        assertNull(KitProtocol.parseAd(changedVerdict, key))
    }

    @Test fun malformedAdvertisementsNeverThrow() {
        val ad = hex(vector)
        assertNull(KitProtocol.parseAd(null, key))
        assertNull(KitProtocol.parseAd(ByteArray(0), key))
        assertNull(KitProtocol.parseAd(ad.copyOf(15), key))
        assertNull(KitProtocol.parseAd(KitProtocol.encodeAd(message, key, companyId = 0x004C), key))
    }

    @Test fun finderSkipsOtherStructuresAndTrailingPadding() {
        val name = byteArrayOf(5, 0x09, 0x41, 0x42, 0x43, 0x44)
        val ad = byteArrayOf(2, 0x01, 0x06) + name + hex(vector).copyOfRange(3, 23) + ByteArray(8)
        assertEquals(message, KitProtocol.parseAd(ad, key))
    }

    @Test fun badSignatureStillLooksLikeAKitSoItCanBeCountedSeparately() {
        val bad = hex(vector).also { it[it.size - 1] = 0 }
        assertTrue(KitProtocol.looksLikeKit(bad))
        assertNull(KitProtocol.parseAd(bad, key))
        assertFalse(KitProtocol.looksLikeKit(hex("0201060303180f")))
    }

    @Test fun fieldRangesAreChecked() {
        assertThrows(IllegalArgumentException::class.java) { KitMessage(300, 0, KitVerdict.PASS, 0) }
        assertThrows(IllegalArgumentException::class.java) { KitMessage(1, 0, KitVerdict.PASS, 101) }
    }

    @Test fun replayFilterDropsRepeatsAndOldMessagesAndHandlesWraparound() {
        val filter = KitReplayFilter()
        assertTrue(filter.isNew(message))
        assertFalse(filter.isNew(message))
        assertTrue(filter.isNew(message.copy(seq = 43)))
        assertFalse(filter.isNew(message))
        assertTrue(filter.isNew(KitMessage(4, 1, KitVerdict.PASS, 0)))
        val wrap = KitReplayFilter()
        assertTrue(wrap.isNew(KitMessage(1, 65535, KitVerdict.PASS, 0)))
        assertTrue(wrap.isNew(KitMessage(1, 0, KitVerdict.PASS, 0)))
    }
}
