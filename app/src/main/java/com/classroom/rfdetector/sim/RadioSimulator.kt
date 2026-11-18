package com.classroom.rfdetector.sim

import com.classroom.rfdetector.core.RfObservation
import com.classroom.rfdetector.core.ScanSource
import java.util.Random
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max

/**
 * 에뮬레이터/실기기 검증용 가상 RF 환경.
 * 실제 스캐너와 동일한 관측 형식(RfObservation)을 만들어 같은 파이프라인을 탄다.
 * 좌표계: 북=+y, 동=+x. 사용자는 원점에 있다.
 */
class RadioSimulator {
    class VDev(
        val key: String,
        val name: String,
        val source: ScanSource,
        val hidden: Boolean,
        var x: Double,
        var y: Double,
        val txDbm: Double = -59.0,
    )

    val devices = mutableListOf(
        VDev("sim-airpods", "AirPods Pro (가상)", ScanSource.BLE, false, x = 1.3, y = 2.6),
        VDev("sim-buds", "Galaxy Buds (가상)", ScanSource.BLE, false, x = -2.6, y = 1.4),
        VDev("sim-watch", "스마트워치 (가상)", ScanSource.BLE, false, x = 0.5, y = 0.9),
        VDev("sim-hotspot", "휴대폰 핫스팟 (가상)", ScanSource.WIFI, false, x = 3.8, y = -4.2),
        VDev("sim-hidden", "(이름 없는 AP · 가상)", ScanSource.WIFI, true, x = -4.6, y = -3.2),
    )

    private val rnd = Random()

    fun reset() {
        devices.removeAll { it.key.startsWith("sim-extra-") }
    }

    /** 이름 없이 Meta 회사 코드만 내는 가상 기기. 스마트 안경 회사 단서 화면을 확인하는 테스트용이다. */
    fun addGlassesCandidate() {
        if (devices.none { it.key == "sim-extra-glasses" }) devices.add(VDev("sim-extra-glasses", "", ScanSource.BLE, true, 0.8, 0.6))
    }

    fun addDevice() {
        val index = devices.count { it.key.startsWith("sim-extra-") } + 1
        devices.add(VDev("sim-extra-$index", "AirPods Pro · 추가 가상 $index", ScanSource.BLE, false, 1.0, 1.0))
    }

    /** 4Hz 정도로 호출: 원점 기준 관측값 생성 */
    fun tick(): List<RfObservation> {
        val now = System.currentTimeMillis()
        return devices.map { d ->
            val dist = max(0.4, hypot(d.x, d.y))
            var rssi = d.txDbm - 10 * 2.7 * log10(dist) + rnd.nextGaussian() * 3.2
            if (rnd.nextDouble() < 0.08) rssi -= 5.0   // 다중경로 페이딩 흉내
            RfObservation(
                key = d.key,
                source = d.source,
                name = d.name.takeIf { !d.hidden },
                rssi = rssi.toInt(),
                txPower = null,
                hidden = d.hidden,
                timestampMs = now,
                hints = if (d.key == "sim-extra-glasses") setOf(com.classroom.rfdetector.core.SignalHint.GLASSES_MAKER) else emptySet(),
                manufacturerIds = when {
                    d.key == "sim-airpods" || (d.key.startsWith("sim-extra-") && d.key != "sim-extra-glasses") -> setOf(76)
                    d.key == "sim-buds" -> setOf(117)
                    d.key == "sim-extra-glasses" -> setOf(0x01AB)
                    else -> emptySet()
                },
            )
        }
    }
}
