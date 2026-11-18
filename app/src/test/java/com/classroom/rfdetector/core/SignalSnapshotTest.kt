package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class SignalSnapshotTest {
    @Test fun captureIsImmutableAndKeepsSignalsNoLongerReceived() {
        val tracker = DeviceTracker()
        tracker.update(RfObservation("brief", ScanSource.BLE, "old", -70, null, false, 1000))
        tracker.update(RfObservation("present", ScanSource.BLE, "current", -55, null, false, 20_000))
        val frozen = SignalSnapshot.capture(" 제1시험실 ", 20_000, false, tracker.snapshot(20_000, true))
        tracker.update(RfObservation("present", ScanSource.BLE, "changed", -40, null, false, 21_000))
        assertEquals("제1시험실", frozen.area)
        val present = frozen.signals.single { it.address == "present" }
        assertEquals("current", present.name)
        assertEquals(-55.0, present.rssi!!, 0.001)
        assertEquals(SignalPresence.HISTORY, frozen.signals.single { it.address == "brief" }.presence)
    }

    @Test fun sameAddressOnTwoRadiosProducesTwoIndependentSnapshotEntries() {
        val tracker = DeviceTracker()
        tracker.update(RfObservation("AA", ScanSource.BLE, "AirPods Pro", -55, null, false, 1000, setOf(76)))
        tracker.update(RfObservation("AA", ScanSource.WIFI, "Room AP", -85, null, true, 2000))
        val after = SignalSnapshot.capture("제1시험실", 2000, false, tracker.snapshot(2000))
        assertEquals(2, after.signals.size)
        val wifi = after.signals.single { it.source == ScanSource.WIFI }
        assertEquals("Room AP", wifi.name)
        assertTrue(wifi.manufacturerIds.isEmpty())
        val ble = after.signals.single { it.source == ScanSource.BLE }
        assertEquals("AirPods Pro", ble.name)
        assertEquals(setOf(76), ble.manufacturerIds)
    }
}
