package com.classroom.rfdetector

import android.content.ContextWrapper
import android.os.Looper
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanSessionTest {
    @get:Rule val directory = TemporaryFolder()
    private lateinit var vm: AppViewModel

    private fun newViewModel(): AppViewModel {
        val app = RuntimeEnvironment.getApplication()
        return AppViewModel(object : ContextWrapper(app) { override fun getFilesDir() = directory.root }).also { vm = it; awaitStorage() }
    }

    @Before fun create() {
        newViewModel()
        vm.setSim(true)
        vm.startScan()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(5, vm.devices.value.size)
    }
    @After fun close() { vm.shutdown() }

    private fun awaitStorage() {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (!vm.snapshotBusy.value) return
            Thread.sleep(5)
        }
        fail("Snapshot operation did not finish")
    }

    private fun saveEmptyRoom() {
        vm.takeBaseline()
        awaitStorage()
        assertTrue(vm.baselineSet.value)
    }

    private fun kitSighting(seq: Int, code: String = "exam-2026-1118") = com.classroom.rfdetector.core.KitSighting(
        com.classroom.rfdetector.core.KitProtocol.encodeAd(
            com.classroom.rfdetector.core.KitMessage(3, seq, com.classroom.rfdetector.core.KitVerdict.CHECK, 71, 17),
            com.classroom.rfdetector.core.KitProtocol.deriveKey(code)), -60, System.currentTimeMillis())

    @Test fun kitJudgmentIsShownSeparatelyAndNeverCountedAsANewSignal() {
        saveEmptyRoom()
        vm.setKitCode("exam-2026-1118")
        vm.onKitSighting(kitSighting(1))
        vm.onKitSighting(kitSighting(1))   // 같은 메시지의 반복 광고
        assertEquals(1, vm.kit.value.events.size)
        assertEquals(1, vm.kit.value.checkCount)
        assertEquals(0, vm.watch.value.count)
        vm.setKitCode("wrong-code-0000")
        vm.onKitSighting(kitSighting(2))
        assertEquals(1, vm.kit.value.unreadable)
        assertEquals(1, vm.kit.value.events.size)   // 코드를 바꿔도 이미 받은 판정은 남는다
        assertEquals(0, vm.watch.value.count)
    }

    @Test fun emptyRoomRecordStartsWatchingAndOnlyNewKindsAreFlagged() {
        assertFalse(vm.baselineSet.value)
        assertEquals(0, vm.watch.value.count)
        saveEmptyRoom()
        assertEquals(0, vm.watch.value.count)
        assertEquals(5, vm.watch.value.knownGroups.size)

        vm.simAddDevice()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val group = vm.watch.value.bluetooth.single()
        assertEquals(0, group.baselineCount)

        vm.acknowledge(group.strongest)
        assertEquals(0, vm.watch.value.count)
    }

    @Test fun leavingAndReturningContinuesTheSameSessionAndCountsTheGap() {
        saveEmptyRoom()
        val session = vm.scanSession.value!!
        vm.acknowledge(vm.devices.value.first())
        vm.pauseForBackground()
        assertFalse(vm.scanning.value)
        vm.resumeFromBackground()
        assertTrue(vm.scanning.value)
        assertEquals(session.id, vm.scanSession.value!!.id)
        assertEquals(1, vm.scanSession.value!!.pauses)
        assertEquals(5, vm.devices.value.size)
        assertTrue(vm.baselineSet.value)
        assertEquals(1, vm.acknowledged.value.size)

        vm.stopScan()
        vm.resumeFromBackground()
        assertFalse("A manual stop stays stopped", vm.scanning.value)
        vm.startScan()
        assertNotEquals(session.id, vm.scanSession.value!!.id)
        assertTrue("The same empty-room record keeps being used", vm.baselineSet.value)
        assertTrue(vm.acknowledged.value.isEmpty())
    }

    @Test fun restartingFromTheEmptyRoomClearsTheRecordAndCollectsAgain() {
        saveEmptyRoom()
        val session = vm.scanSession.value!!.id
        vm.restartFromEmptyRoom()
        assertTrue(vm.scanning.value)
        assertFalse(vm.baselineSet.value)
        assertNull(vm.baselineSnapshot.value)
        assertNotEquals(session, vm.scanSession.value!!.id)
    }

    @Test fun onlyTheLatestRealEmptyRoomRecordIsOfferedAfterReopening() {
        saveEmptyRoom()
        vm.shutdown()
        // 가상 전파로 저장한 기록은 실제 감시에 이어 쓰지 않는다.
        assertNull(newViewModel().savedBaseline.value)
    }
}
