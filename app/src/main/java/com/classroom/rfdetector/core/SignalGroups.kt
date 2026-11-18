package com.classroom.rfdetector.core

import java.util.Locale

/** 새 주소가 이 시간 넘게 이어져야 "늘어났다"고 본다. 주소가 바뀌는 순간 옛 주소와 겹치는 것을 거른다. */
const val ESTABLISHED_MS = 30_000L

/**
 * 새 와이파이를 바로 알리는 세기. 이보다 약한(거리 어림 3m 넘게, "중간"·"멀다") 새 와이파이는
 * WIFI_PERSIST_MS 넘게 계속 잡혀야 알린다. 현장 측정(2026-11-18)에서 오경보는 모두 이웃 공유기가
 * "중간"·"멀다" 세기로 잠깐씩 잡혔다 사라진 것이었다. 같은 교실의 핫스팟은 대개 이보다 세게 잡혀 바로 알린다.
 */
const val WIFI_NEAR_DBM = -72.0
/** 약한 새 와이파이가 이만큼 이어서 잡혀야 알린다. 와이파이 탐색이 30초 간격이라 약 4회 연속에 해당한다. */
const val WIFI_PERSIST_MS = 120_000L

/**
 * 주소 대신 쓰는 묶음 기준. 블루투스 주소는 10~15분마다 바뀌므로 기기가 알린 이름,
 * 이름이 없으면 회사 코드와 종류 단서로 묶는다. 와이파이는 이름으로 묶는다.
 */
fun signalGroupKey(source: ScanSource, address: String, name: String, manufacturerIds: Set<Int>, hints: Set<SignalHint>): String {
    val advertised = advertisedName(address, name)
    return when {
        advertised != null -> "${source.name}:name:${advertised.lowercase(Locale.ROOT)}"
        source == ScanSource.WIFI -> "WIFI:hidden"
        manufacturerIds.isEmpty() && hints.isEmpty() -> "BLE:unknown"
        // GLASSES_MAKER는 묶음 기준에서 뺀다. 회사 코드는 이미 기준에 들어 있고, 이 단서를 넣으면
        // 이전 버전이 저장한 빈 교실 기록의 묶음과 키가 달라져 원래 있던 기기가 새 전파로 뜬다.
        else -> "BLE:kind:${manufacturerIds.sorted().joinToString(",")}:${(hints - SignalHint.GLASSES_MAKER).primary?.name.orEmpty()}"
    }
}

/** 기기가 실제로 알린 이름. 앱이 대신 붙인 이름(주소 끝자리 등)이면 null이다. */
fun advertisedName(address: String, name: String): String? {
    val tail = address.takeLast(5)
    return name.trim().takeUnless { it.isEmpty() || it == "이름 없는 AP" || it == "Wi-Fi 기기 $tail" || it == "BLE 기기 $tail" }
}

val TrackedDevice.groupKey: String get() = signalGroupKey(source, key, displayName, manufacturerIds, hints)
val SnapshotSignal.groupKey: String get() = signalGroupKey(source, address, name, manufacturerIds, hints)

/** 빈 교실 기록에서 묶음마다 동시에 몇 개였는지. 저장 순간 잡히지 않던 묶음은 1개로 친다. */
fun baselineGroupCounts(baseline: SignalSnapshot): Map<String, Int> = baseline.signals.groupBy { it.groupKey }
    .mapValues { (_, signals) -> signals.count { it.presence != SignalPresence.HISTORY }.coerceAtLeast(1) }

/** 지금 잡히는 한 묶음. devices는 센 순서다. */
data class SignalGroup(val key: String, val devices: List<TrackedDevice>, val baselineCount: Int, val alert: Boolean) {
    val strongest: TrackedDevice get() = devices.first()
}

/**
 * "원래 있던 전파" 목록의 한 줄. 빈 교실 기록의 묶음마다 하나씩 있고 순서가 바뀌지 않는다.
 * 지금 잡히는지에 따라 live만 달라진다. sample은 빈 교실 기록의 대표, last는 가장 최근에 잡힌 주소다.
 */
data class KnownGroup(val key: String, val sample: SnapshotSignal?, val baselineCount: Int, val live: List<TrackedDevice>, val last: TrackedDevice?)

/** 빈 교실 기록의 묶음별 대표를 고정된 순서로: 와이파이 먼저, 그 안에서는 기록 당시 센 순서. */
fun baselineSamples(baseline: SignalSnapshot): List<SnapshotSignal> = baseline.signals
    .filterNot { it.source == ScanSource.BLE && it.hints.primary == SignalHint.APPLE_FIND_MY }
    .groupBy { it.groupKey }.values.map { group -> group.maxBy { it.rssi ?: Double.NEGATIVE_INFINITY } }
    .sortedWith(compareBy<SnapshotSignal> { it.source != ScanSource.WIFI }.thenByDescending { it.rssi ?: Double.NEGATIVE_INFINITY }.thenBy { it.groupKey })

/** 감시 화면에 보이는 것 전부. 새 전파는 지금 잡히는 것만 담고 누적하지 않는다. */
data class WatchView(
    val wifi: List<SignalGroup> = emptyList(),
    val bluetooth: List<SignalGroup> = emptyList(),
    val far: List<SignalGroup> = emptyList(),
    val findMy: List<TrackedDevice> = emptyList(),
    val knownGroups: List<KnownGroup> = emptyList(),
) {
    val count: Int get() = wifi.size + bluetooth.size
}

/** 빈 교실 기록에 있던 와이파이 주소. 같은 공유기가 내는 다른 주소를 알아보는 데 쓴다. */
fun baselineWifiAddresses(baseline: SignalSnapshot): Set<String> =
    baseline.signals.filter { it.source == ScanSource.WIFI }.map { it.address.uppercase(Locale.ROOT) }.toSet()

/**
 * 공유기 한 대는 이름별·대역별로 주소를 여러 개 쓰는데, 이 주소들은 첫 자리나 끝 자리만 다르다.
 * 빈 교실 기록의 주소와 나머지 다섯 자리가 같으면 같은 공유기가 낸 것으로 본다.
 */
fun isSameAccessPoint(address: String, baselineWifi: Set<String>): Boolean {
    val candidate = address.uppercase(Locale.ROOT)
    if (candidate in baselineWifi) return true
    if (candidate.length != 17) return false
    return baselineWifi.any { known -> known.length == 17 && (known.substring(3) == candidate.substring(3) || known.dropLast(2) == candidate.dropLast(2)) }
}

/**
 * 감독관이 "확인함"으로 표시한 전파.
 * - 와이파이: 주소(BSSID)가 바뀌지 않으므로 그 주소만 원래 있던 것으로 본다. 같은 이름·이름 없는 다른 와이파이는 계속 알린다.
 * - 블루투스: 주소가 10~15분마다 바뀌므로 주소로는 기억할 수 없다. 대신 그 묶음의 허용 개수를 1 늘린다.
 *   같은 종류의 기기가 하나 더 오면 다시 알린다.
 * keys: 이미 확인한 주소(trackingKey). 같은 주소를 두 번 눌러 허용 개수가 두 번 늘지 않게 한다.
 */
data class Acknowledged(
    val wifi: Set<String> = emptySet(),
    val ble: Map<String, Int> = emptyMap(),
    val keys: Set<String> = emptySet(),
) {
    val size: Int get() = keys.size
    fun isEmpty(): Boolean = keys.isEmpty()

    fun plus(device: TrackedDevice): Acknowledged {
        if (device.trackingKey in keys) return this
        return if (device.source == ScanSource.WIFI) copy(wifi = wifi + device.key.uppercase(Locale.ROOT), keys = keys + device.trackingKey)
        else copy(ble = ble + (device.groupKey to (ble[device.groupKey] ?: 0) + 1), keys = keys + device.trackingKey)
    }
}

fun watchView(devices: List<TrackedDevice>, baseline: Map<String, Int>, acknowledged: Acknowledged, nowMs: Long, baselineWifi: Set<String> = emptySet(),
    samples: List<SnapshotSignal> = emptyList()): WatchView {
    val live = devices.filter { it.presence != SignalPresence.HISTORY }
    // 전원을 끈 아이폰과 에어태그가 내는 신호. 제출한 휴대폰 수만큼 잡힐 수 있어 알리지 않고 따로 센다.
    val (findMy, others) = live.partition { it.source == ScanSource.BLE && it.hints.primary == SignalHint.APPLE_FIND_MY }
    // 빈 교실에 있던 공유기(와 확인함으로 표시한 와이파이)가 내는 주소는 원래 있던 것이다. 이름을 숨긴 것도 포함한다.
    val knownWifi = baselineWifi + acknowledged.wifi
    val rest = others.filterNot { it.source == ScanSource.WIFI && isSameAccessPoint(it.key, knownWifi) }
    val flagged = mutableListOf<SignalGroup>()
    // 약하고 잠깐만 잡힌 새 와이파이. 알리지 않고 "멀리서 약하게·잠깐 잡힌 것"에 모은다.
    val brief = mutableListOf<SignalGroup>()
    rest.groupBy { it.groupKey }.forEach { (key, members) ->
        // 확인함으로 표시한 주소는 뒤로 보내, 대표(맨 앞)가 아직 확인하지 않은 것이 되게 한다.
        val sorted = members.sortedWith(compareBy<TrackedDevice> { it.trackingKey in acknowledged.keys }
            .thenByDescending { it.rssiEma ?: Double.NEGATIVE_INFINITY })
        val before = baseline[key] ?: 0
        val wifi = sorted.first().source == ScanSource.WIFI
        val allowed = before + (acknowledged.ble[key] ?: 0)
        val established = members.count { nowMs - (it.periods.lastOrNull()?.firstSeenMs ?: it.firstSeenMs) >= ESTABLISHED_MS }
        val group = when {
            // 여기까지 남은 와이파이는 빈 교실의 어느 공유기와도 주소가 이어지지 않는 새 주소다.
            // 이름이 같거나(흉내 낸 이름) 이름을 숨겼어도(숨긴 핫스팟) 알린다. 와이파이 주소는 바뀌지 않으므로 바로 알린다.
            // 다만 약하게 잡히는 것은 이웃 공유기가 잠깐 잡혔다 사라지는 경우가 많아, 계속 잡힐 때만 알린다.
            wifi -> {
                val near = (sorted.first().rssiEma ?: Double.NEGATIVE_INFINITY) >= WIFI_NEAR_DBM
                val persistent = members.any { it.lastSeenMs - (it.periods.lastOrNull()?.firstSeenMs ?: it.firstSeenMs) >= WIFI_PERSIST_MS }
                if (near || persistent) SignalGroup(key, sorted, 0, alert = true)
                else { brief += SignalGroup(key, sorted, 0, alert = false); null }
            }
            // 빈 교실에 없던 묶음은 바로 보이고, 30초 넘게 이어지면 알린다.
            allowed == 0 -> SignalGroup(key, sorted, 0, alert = established > 0)
            established > allowed -> SignalGroup(key, sorted, before, alert = true)
            else -> null
        }
        if (group != null) flagged += group
    }
    val byStrength = compareByDescending<SignalGroup> { it.strongest.rssiEma ?: Double.NEGATIVE_INFINITY }
    val (far, near) = flagged.sortedWith(byStrength).partition { (it.strongest.rssiEma ?: -127.0) < WEAK_SIGNAL_DBM }
    return WatchView(
        wifi = near.filter { it.strongest.source == ScanSource.WIFI },
        bluetooth = near.filter { it.strongest.source != ScanSource.WIFI },
        far = far + brief.sortedWith(byStrength),
        findMy = findMy,
        knownGroups = knownGroups(devices, baseline, acknowledged, knownWifi, samples),
    )
}

/**
 * 빈 교실 기록의 묶음을 고정된 순서로 나열하고, 그 뒤에 감시 중 원래 있던 것으로 판단된 묶음
 * (같은 공유기의 다른 주소, 확인함으로 표시한 것)을 처음 잡힌 순서로 붙인다. 사라져도 줄은 남는다.
 */
private fun knownGroups(devices: List<TrackedDevice>, baseline: Map<String, Int>, acknowledged: Acknowledged, knownWifi: Set<String>,
    samples: List<SnapshotSignal>): List<KnownGroup> {
    val byStrength = compareByDescending<TrackedDevice> { it.rssiEma ?: Double.NEGATIVE_INFINITY }
    val members = devices.filterNot { it.source == ScanSource.BLE && it.hints.primary == SignalHint.APPLE_FIND_MY }.groupBy { it.groupKey }
    fun row(key: String, sample: SnapshotSignal?): KnownGroup {
        val all = members[key].orEmpty()
        return KnownGroup(key, sample, baseline[key] ?: 0, all.filter { it.presence != SignalPresence.HISTORY }.sortedWith(byStrength), all.maxByOrNull { it.lastSeenMs })
    }
    val fromEmptyRoom = samples.map { row(it.groupKey, it) }
    val recorded = samples.map { it.groupKey }.toSet()
    val later = members.filter { (key, all) -> key !in recorded && (key in acknowledged.ble || all.any { it.source == ScanSource.WIFI && isSameAccessPoint(it.key, knownWifi) }) }
        .entries.sortedWith(compareBy({ entry -> entry.value.minOf { it.firstSeenMs } }, { it.key })).map { row(it.key, null) }
    return fromEmptyRoom + later
}
