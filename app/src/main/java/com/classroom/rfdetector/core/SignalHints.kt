package com.classroom.rfdetector.core

/**
 * 광고 데이터에서 읽은 종류 단서. 기기가 스스로 알린 값이라 후보일 뿐 인증이 아니다.
 * 선언 순서가 대표 표시의 우선순위다(구체적인 것 먼저).
 */
enum class SignalHint(val label: String) {
    GLASSES("스마트 안경"),
    /** 회사 코드나 서비스 번호만 보고 붙인다. 같은 회사의 다른 기기(헤드셋 등)일 수 있다. */
    GLASSES_MAKER("스마트 안경 회사 기기 후보"),
    EARPHONES("무선 이어폰·헤드셋"),
    APPLE_AUDIO("Apple·Beats 이어폰"),
    WATCH("스마트워치"),
    HEARING_AID("보청기"),
    PHONE("휴대폰"),
    APPLE_ACTIVE("켜져 있는 Apple 기기 · 아이폰·아이패드·워치 등"),
    COMPUTER("노트북·컴퓨터"),
    WINDOWS("Windows 기기"),
    FAST_PAIR("블루투스 액세서리 · 이어폰 등"),
    TAG("위치 추적 태그"),
    APPLE_FIND_MY("나의 찾기 신호 · 에어태그나 꺼 둔 Apple 기기"),
    BEACON("고정 비콘"),
    HOTSPOT("휴대폰 핫스팟 후보"),
    WIFI_DIRECT("Wi-Fi Direct 기기 · 휴대폰·프린터 등"),
}

val Set<SignalHint>.primary: SignalHint? get() = minByOrNull { it.ordinal }

private const val APPLE = 0x004C
private const val MICROSOFT = 0x0006
private const val FAST_PAIR_UUID = 0xFE2C

/**
 * 스마트 안경을 만드는 회사의 블루투스 회사 코드. 앱에 넣은 Bluetooth SIG 등록부에서 이름을 확인했다.
 * 0x01AB Meta Platforms, 0x058E Meta Platforms Technologies, 0x0D53 Luxottica, 0x10F9 Even Realities, 0x060C Vuzix.
 * 이 코드가 실제 안경 광고에 쓰이는지는 0x01AB 외에는 실물로 확인하지 않았다.
 */
private val GLASSES_COMPANIES = setOf(0x01AB, 0x058E, 0x0D53, 0x10F9, 0x060C)
/** 오픈소스 Ban-Rays 프로젝트가 Meta 기기 감지에 쓴 16비트 서비스 UUID. 실물 캡처로 확인하지 않았다. */
private const val META_SERVICE_UUID = 0xFD5F

/** BLE 광고 원본(AD 구조 나열)에서 Appearance, 서비스 UUID, 제조사 데이터를 읽는다. 잘린 구조는 무시한다. */
fun bleHints(advertisement: ByteArray?): Set<SignalHint> {
    if (advertisement == null) return emptySet()
    val hints = sortedSetOf<SignalHint>()
    var index = 0
    while (index < advertisement.size) {
        val length = advertisement[index].toInt() and 0xFF
        if (length == 0 || index + length >= advertisement.size) break
        val type = advertisement[index + 1].toInt() and 0xFF
        val data = advertisement.copyOfRange(index + 2, index + 1 + length)
        fun u16(at: Int) = (data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8)
        when (type) {
            0x19 -> if (data.size >= 2) when (u16(0) shr 6) {
                0x01 -> hints += SignalHint.PHONE
                0x02 -> hints += SignalHint.COMPUTER
                0x03 -> hints += SignalHint.WATCH
                0x07 -> hints += SignalHint.GLASSES
                0x08 -> hints += SignalHint.TAG
                0x25 -> hints += SignalHint.EARPHONES
                0x29 -> hints += SignalHint.HEARING_AID
            }
            0x02, 0x03 -> {
                val uuids = (0 until data.size - 1 step 2).map { u16(it) }
                if (FAST_PAIR_UUID in uuids) hints += SignalHint.FAST_PAIR
                if (META_SERVICE_UUID in uuids) hints += SignalHint.GLASSES_MAKER
            }
            0x16 -> if (data.size >= 2) when (u16(0)) {
                FAST_PAIR_UUID -> hints += SignalHint.FAST_PAIR
                META_SERVICE_UUID -> hints += SignalHint.GLASSES_MAKER
            }
            0xFF -> if (data.size >= 3) when (u16(0)) {
                APPLE -> hints += appleHints(data.copyOfRange(2, data.size))
                MICROSOFT -> if (data[2].toInt() == 0x01) hints += SignalHint.WINDOWS
                in GLASSES_COMPANIES -> hints += SignalHint.GLASSES_MAKER
            }
        }
        index += length + 1
    }
    return hints
}

/** Apple 제조사 데이터는 [종류][길이][내용]이 이어진다. */
private fun appleHints(payload: ByteArray): Set<SignalHint> {
    val hints = mutableSetOf<SignalHint>()
    var index = 0
    while (index + 1 < payload.size) {
        when (payload[index].toInt() and 0xFF) {
            0x02 -> hints += SignalHint.BEACON
            0x07 -> hints += SignalHint.APPLE_AUDIO
            0x05, 0x0C, 0x0E, 0x0F, 0x10 -> hints += SignalHint.APPLE_ACTIVE
            0x12 -> hints += SignalHint.APPLE_FIND_MY
        }
        index += 2 + (payload[index + 1].toInt() and 0xFF)
    }
    return hints
}

private val hotspotName = Regex("iPhone|iPad|Galaxy|AndroidAP|Pixel[ _]|Redmi|Xiaomi|POCO|HUAWEI|OnePlus|hotspot|핫스팟|테더링", RegexOption.IGNORE_CASE)

/** 휴대폰 기본 핫스팟 이름과 Wi-Fi Direct 이름 규칙. 이름은 바꿀 수 있으므로 없다고 핫스팟이 아닌 것은 아니다. */
fun wifiHints(ssid: String?): Set<SignalHint> = when {
    ssid.isNullOrBlank() -> emptySet()
    ssid.startsWith("DIRECT-") -> setOf(SignalHint.WIFI_DIRECT)
    hotspotName.containsMatchIn(ssid) -> setOf(SignalHint.HOTSPOT)
    else -> emptySet()
}
