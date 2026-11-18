package com.classroom.rfdetector.core

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/*
 * 광학 키트가 BLE 광고로 보내는 판정 메시지(프로토콜 v1). 기준 구현은 software/kit_beacon.py.
 * 광고에는 기밀 보장이 없다. 서명(tag)은 위조와 재전송을 거르기 위한 것이다.
 * 좌석 번호만 담고 수험번호는 담지 않는다.
 */

/** 키트의 판정. 부정행위 확정이 아니라 확인이 필요한 정도를 뜻한다. */
enum class KitVerdict(val code: Int, val label: String) {
    PASS(0, "이상 없음"),
    CHECK(1, "확인 필요"),
    RETRY(2, "다시 촬영"),
    FAULT(3, "키트 오류");

    companion object {
        fun fromCode(code: Int): KitVerdict? = entries.firstOrNull { it.code == code }
    }
}

/** seat 0 은 좌석을 지정하지 않았다는 뜻이다. */
data class KitMessage(val kitId: Int, val seq: Int, val verdict: KitVerdict, val score: Int, val seat: Int = 0) {
    init {
        require(kitId in 0..255 && seq in 0..0xFFFF && score in 0..100 && seat in 0..0xFFFF) { "필드 범위 오류" }
    }
}

object KitProtocol {
    /** Bluetooth SIG 시험용 회사 ID. 실사용 전에 정식 회사 ID나 서비스 UUID가 필요하다. */
    const val COMPANY_ID_TEST = 0xFFFF
    const val VERSION = 1
    private const val TAG_LEN = 6
    private const val PAYLOAD_LEN = 16
    private val MAGIC = byteArrayOf('K'.code.toByte(), 'S'.code.toByte())

    /**
     * 감독관이 입력하는 짧은 키트 코드에서 16바이트 키를 만든다. 키트(software/kit_beacon.py derive_key)와 같은 규칙이다.
     * 키 = SHA-256("KoriyoKit/v1|" + 코드(앞뒤 공백 제거))의 앞 16바이트.
     */
    fun deriveKey(code: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(("KoriyoKit/v1|" + code.trim()).toByteArray(Charsets.UTF_8)).copyOf(16)

    private fun body(m: KitMessage): ByteArray = byteArrayOf(
        VERSION.toByte(), m.kitId.toByte(),
        (m.seq and 0xFF).toByte(), ((m.seq shr 8) and 0xFF).toByte(),
        m.verdict.code.toByte(), m.score.toByte(),
        (m.seat and 0xFF).toByte(), ((m.seat shr 8) and 0xFF).toByte(),
    )

    private fun tag(key: ByteArray, body: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(body).copyOf(TAG_LEN)
    }

    fun encodePayload(m: KitMessage, key: ByteArray): ByteArray {
        val b = body(m)
        return MAGIC + b + tag(key, b)
    }

    /** Flags + 제조사 데이터 AD (23바이트). 키트 쪽 구현과 같은 바이트를 만드는지 시험하는 용도다. */
    fun encodeAd(m: KitMessage, key: ByteArray, companyId: Int = COMPANY_ID_TEST): ByteArray {
        val mfr = byteArrayOf((companyId and 0xFF).toByte(), ((companyId shr 8) and 0xFF).toByte()) + encodePayload(m, key)
        return byteArrayOf(2, 0x01, 0x06, (mfr.size + 1).toByte(), 0xFF.toByte()) + mfr
    }

    /**
     * 광고 원본(AD 구조 나열)에서 키트 메시지를 찾아 서명을 검증한다.
     * 형식이 다르거나 서명이 맞지 않거나 잘린 광고는 null 이다. 예외를 던지지 않는다.
     */
    fun parseAd(advertisement: ByteArray?, key: ByteArray, companyId: Int = COMPANY_ID_TEST): KitMessage? {
        val data = kitPayload(advertisement, companyId) ?: return null
        val body = data.copyOfRange(2, 10)
        val expected = tag(key, body)
        if (!MessageDigest.isEqual(expected, data.copyOfRange(10, PAYLOAD_LEN))) return null
        val seq = (body[2].toInt() and 0xFF) or ((body[3].toInt() and 0xFF) shl 8)
        val seat = (body[6].toInt() and 0xFF) or ((body[7].toInt() and 0xFF) shl 8)
        val verdict = KitVerdict.fromCode(body[4].toInt() and 0xFF) ?: return null
        val score = body[5].toInt() and 0xFF
        if (score > 100) return null
        return KitMessage(body[1].toInt() and 0xFF, seq, verdict, score, seat)
    }

    /** 키트 형식(회사 ID·magic·버전·길이)은 맞지만 서명은 아직 확인하지 않은 payload. 서명 불일치 신호를 따로 세는 데 쓴다. */
    fun looksLikeKit(advertisement: ByteArray?, companyId: Int = COMPANY_ID_TEST): Boolean =
        kitPayload(advertisement, companyId) != null

    private fun kitPayload(advertisement: ByteArray?, companyId: Int): ByteArray? {
        if (advertisement == null) return null
        var index = 0
        while (index < advertisement.size) {
            val length = advertisement[index].toInt() and 0xFF
            if (length == 0 || index + 1 + length > advertisement.size) break
            val type = advertisement[index + 1].toInt() and 0xFF
            val data = advertisement.copyOfRange(index + 2, index + 1 + length)
            if (type == 0xFF && data.size == 2 + PAYLOAD_LEN) {
                val company = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
                val payload = data.copyOfRange(2, data.size)
                if (company == companyId && payload[0] == MAGIC[0] && payload[1] == MAGIC[1] && (payload[2].toInt() and 0xFF) == VERSION) return payload
            }
            index += length + 1
        }
        return null
    }
}

/** 같은 메시지의 반복 광고와 재전송을 거른다. 키트별 seq 가 커질 때만 새 메시지로 인정한다(u16 순환 처리). */
class KitReplayFilter {
    private val last = mutableMapOf<Int, Int>()

    @Synchronized
    fun isNew(m: KitMessage): Boolean {
        val previous = last[m.kitId]
        if (previous != null && ((m.seq - previous) and 0xFFFF) !in 1 until 0x8000) return false
        last[m.kitId] = m.seq
        return true
    }
}
