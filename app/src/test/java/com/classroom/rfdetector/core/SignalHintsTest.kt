package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SignalHintsTest {
    @get:Rule val directory = TemporaryFolder()

    private fun ad(type: Int, vararg data: Int) = byteArrayOf((data.size + 1).toByte(), type.toByte()) + data.map { it.toByte() }
    private val flags = ad(0x01, 0x06)

    @Test fun appearanceCategoryNamesTheDeviceKind() {
        assertEquals(setOf(SignalHint.WATCH), bleHints(flags + ad(0x19, 0xC1, 0x00)))
        assertEquals(setOf(SignalHint.GLASSES), bleHints(ad(0x19, 0xC0, 0x01)))
        assertEquals(setOf(SignalHint.EARPHONES), bleHints(ad(0x19, 0x41, 0x09)))
        assertEquals(setOf(SignalHint.HEARING_AID), bleHints(ad(0x19, 0x40, 0x0A)))
        assertEquals(setOf(SignalHint.PHONE), bleHints(ad(0x19, 0x40, 0x00)))
        assertEquals(emptySet<SignalHint>(), bleHints(ad(0x19, 0x00, 0x00)))
    }

    @Test fun appleMessagesSeparateActiveDevicesEarbudsAndFindMyBeacons() {
        // Handoff(0x0C) 뒤에 Nearby Info(0x10)가 이어지는 켜진 아이폰의 광고
        assertEquals(setOf(SignalHint.APPLE_ACTIVE), bleHints(flags + ad(0xFF, 0x4C, 0x00, 0x0C, 0x02, 0xAA, 0xBB, 0x10, 0x02, 0x01, 0x18)))
        assertEquals(setOf(SignalHint.APPLE_AUDIO), bleHints(ad(0xFF, 0x4C, 0x00, 0x07, 0x02, 0x01, 0x02)))
        assertEquals(setOf(SignalHint.APPLE_FIND_MY), bleHints(ad(0xFF, 0x4C, 0x00, 0x12, 0x02, 0x00, 0x00)))
        // 다른 회사가 같은 종류 바이트를 써도 Apple로 읽지 않는다.
        assertEquals(emptySet<SignalHint>(), bleHints(ad(0xFF, 0x75, 0x00, 0x10, 0x02, 0x01, 0x18)))
    }

    @Test fun fastPairWindowsAndPriorityAreRead() {
        assertEquals(setOf(SignalHint.FAST_PAIR), bleHints(ad(0x16, 0x2C, 0xFE, 0x01, 0x02, 0x03)))
        assertEquals(setOf(SignalHint.FAST_PAIR), bleHints(ad(0x03, 0x0F, 0x18, 0x2C, 0xFE)))
        assertEquals(setOf(SignalHint.WINDOWS), bleHints(ad(0xFF, 0x06, 0x00, 0x01, 0x09)))
        val both = bleHints(ad(0x19, 0xC1, 0x00) + ad(0xFF, 0x4C, 0x00, 0x10, 0x02, 0x01, 0x18))
        assertEquals(setOf(SignalHint.WATCH, SignalHint.APPLE_ACTIVE), both)
        assertEquals(SignalHint.WATCH, both.primary)
    }

    @Test fun truncatedOrEmptyAdvertisementIsIgnored() {
        assertEquals(emptySet<SignalHint>(), bleHints(null))
        assertEquals(emptySet<SignalHint>(), bleHints(byteArrayOf(0, 0, 0)))
        assertEquals(emptySet<SignalHint>(), bleHints(byteArrayOf(9, 0x19, 0xC1.toByte())))
        assertEquals(emptySet<SignalHint>(), bleHints(byteArrayOf(2, 0xFF.toByte(), 0x4C)))
        assertEquals(emptySet<SignalHint>(), bleHints(ad(0xFF, 0x4C, 0x00, 0x10)))
    }

    @Test fun phoneHotspotAndWifiDirectNamesAreCandidatesOnly() {
        assertEquals(setOf(SignalHint.HOTSPOT), wifiHints("홍길동의 iPhone"))
        assertEquals(setOf(SignalHint.HOTSPOT), wifiHints("Galaxy S24 1234"))
        assertEquals(setOf(SignalHint.HOTSPOT), wifiHints("AndroidAP_5521"))
        assertEquals(setOf(SignalHint.WIFI_DIRECT), wifiHints("DIRECT-a1-Galaxy S24"))
        assertEquals(emptySet<SignalHint>(), wifiHints("SCHOOL_WIFI_5G"))
        assertEquals(emptySet<SignalHint>(), wifiHints(null))
    }

    @Test fun hintsFlowFromObservationToSavedSnapshotAndEquipmentType() {
        val tracker = DeviceTracker()
        tracker.update(RfObservation("AA:BB:CC:00:00:01", ScanSource.BLE, null, -60, null, false, 1000, hints = setOf(SignalHint.APPLE_ACTIVE)))
        tracker.update(RfObservation("AA:BB:CC:00:00:01", ScanSource.BLE, null, -60, null, false, 2000, hints = setOf(SignalHint.WATCH)))
        tracker.update(RfObservation("AA:BB:CC:00:00:02", ScanSource.WIFI, "Galaxy S24", -90, null, false, 2000, hints = wifiHints("Galaxy S24")))
        val devices = tracker.snapshot(2000)
        val watch = devices.first { it.source == ScanSource.BLE }
        assertEquals(setOf(SignalHint.APPLE_ACTIVE, SignalHint.WATCH), watch.hints)

        val store = SnapshotStore(directory.root)
        store.save(listOf(SignalSnapshot.capture("A", 3000, false, devices)))
        val loaded = store.load().single().signals.associateBy { it.source }
        assertEquals(setOf(SignalHint.APPLE_ACTIVE, SignalHint.WATCH), loaded.getValue(ScanSource.BLE).hints)
    }

    @Test fun glassesMakerCompanyCodesAndServiceUuidAreCandidatesOnly() {
        // 제조사 데이터: 회사 코드(리틀 엔디언) + 내용 1바이트
        assertEquals(setOf(SignalHint.GLASSES_MAKER), bleHints(flags + ad(0xFF, 0xAB, 0x01, 0x00)))   // Meta 0x01AB
        assertEquals(setOf(SignalHint.GLASSES_MAKER), bleHints(ad(0xFF, 0x8E, 0x05, 0x02)))           // Meta Technologies 0x058E
        assertEquals(setOf(SignalHint.GLASSES_MAKER), bleHints(ad(0xFF, 0x53, 0x0D, 0x00)))           // Luxottica 0x0D53
        assertEquals(setOf(SignalHint.GLASSES_MAKER), bleHints(ad(0xFF, 0xF9, 0x10, 0x00)))           // Even Realities 0x10F9
        assertEquals(setOf(SignalHint.GLASSES_MAKER), bleHints(ad(0xFF, 0x0C, 0x06, 0x00)))           // Vuzix 0x060C
        // 서비스 UUID 0xFD5F: 목록과 서비스 데이터 둘 다
        assertEquals(setOf(SignalHint.GLASSES_MAKER), bleHints(ad(0x03, 0x5F, 0xFD)))
        assertEquals(setOf(SignalHint.GLASSES_MAKER, SignalHint.FAST_PAIR), bleHints(ad(0x03, 0x2C, 0xFE, 0x5F, 0xFD)))
        assertEquals(setOf(SignalHint.GLASSES_MAKER), bleHints(ad(0x16, 0x5F, 0xFD, 0x01)))
        // 다른 회사 코드는 단서를 붙이지 않는다.
        assertEquals(emptySet<SignalHint>(), bleHints(ad(0xFF, 0x75, 0x00, 0x00)))
        // 안경이라고 스스로 알린 Appearance 가 더 구체적이라 대표 단서가 된다.
        assertEquals(SignalHint.GLASSES, bleHints(ad(0x19, 0xC0, 0x01) + ad(0xFF, 0xAB, 0x01, 0x00)).primary)
    }

    @Test fun glassesMakerHintDoesNotChangeTheGroupSoOldEmptyRoomRecordsStillMatch() {
        val without = signalGroupKey(ScanSource.BLE, "7A:11:22:33:44:55", "BLE 기기 44:55", setOf(0x01AB), emptySet())
        val with = signalGroupKey(ScanSource.BLE, "6B:11:22:33:44:66", "BLE 기기 44:66", setOf(0x01AB), setOf(SignalHint.GLASSES_MAKER))
        assertEquals(without, with)
        val watch = signalGroupKey(ScanSource.BLE, "7A:11:22:33:44:55", "BLE 기기 44:55", setOf(0x01AB), setOf(SignalHint.GLASSES_MAKER, SignalHint.WATCH))
        assertTrue(watch.endsWith(":WATCH"))
    }
}
