package com.classroom.rfdetector.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.classroom.rfdetector.core.RfObservation
import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.core.radioKey
import com.classroom.rfdetector.core.wifiDetails
import com.classroom.rfdetector.core.wifiHints
import com.classroom.rfdetector.core.wpsDetails
import kotlinx.coroutines.*

/** Read only completed scans; cached observations keep their actual observation time. */
class WifiScanner(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
    @Volatile var active: Boolean = false
        private set
    @Volatile var lastError: String? = null
        private set
    /** 이번 탐색에서 끝까지 완료된 탐색 횟수. 빈 교실 기록이 충분한지 판단하는 데 쓴다. */
    @Volatile var completedScans: Int = 0
        private set
    private var job: Job? = null
    private var receiver: BroadcastReceiver? = null
    private val observedTimestamps = mutableMapOf<String, Long>()

    fun isEnabled(): Boolean = try { wifi.isWifiEnabled } catch (_: SecurityException) { false }

    fun start(onObs: (RfObservation) -> Unit, periodMs: Long = 30_000L) {
        stop()
        lastError = null
        completedScans = 0
        observedTimestamps.clear()
        val startedAtElapsedUs = SystemClock.elapsedRealtimeNanos() / 1000
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (receiver !== this) return
                if (!intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) {
                    reportError("Wi-Fi 갱신 대기 · 시스템이 탐색 빈도를 제한할 수 있습니다")
                    return
                }
                try {
                    val now = System.currentTimeMillis()
                    val elapsed = SystemClock.elapsedRealtime()
                    val results = wifi.scanResults.filter {
                        it.timestamp >= startedAtElapsedUs && elapsed - it.timestamp / 1000 in 0..90_000
                    }
                    lastError = null
                    completedScans++
                    results.forEach { result ->
                        val key = radioKey(ScanSource.WIFI, result.BSSID)
                        val previousTimestamp = observedTimestamps[key]
                        if (previousTimestamp != null && result.timestamp <= previousTimestamp) return@forEach
                        observedTimestamps[key] = result.timestamp
                        onObs(RfObservation(result.BSSID, ScanSource.WIFI, result.SSID?.takeIf { it.isNotBlank() }, result.level, null,
                            result.SSID.isNullOrBlank(), now - (elapsed - result.timestamp / 1000), hints = wifiHints(result.SSID), details = details(result)))
                    }
                } catch (_: SecurityException) {
                    reportError("Wi-Fi 권한을 확인하세요 · 정확한 위치 권한이 필요합니다")
                }
            }
        }
        receiver = listener
        ContextCompat.registerReceiver(app, listener, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_EXPORTED)
        active = true
        job = scope.launch {
            while (isActive) {
                try {
                    if (!wifi.startScan()) reportError("Wi-Fi 갱신 대기 · Wi-Fi와 위치 설정을 확인하세요")
                } catch (_: SecurityException) {
                    reportError("Wi-Fi 탐색 권한이 없습니다")
                    break
                }
                delay(periodMs)
            }
        }
    }

    private fun details(result: ScanResult): Map<String, String> {
        val modern = Build.VERSION.SDK_INT >= 30
        val details = LinkedHashMap(wifiDetails(result.frequency, result.capabilities, if (modern) result.wifiStandard else null))
        // 부가 정보 블록이 없는 결과에서는 이 조회 자체가 실패할 수 있다.
        if (modern) runCatching { result.informationElements }.getOrNull().orEmpty().filter { it.id == 221 }.forEach { element ->
            val buffer = element.bytes
            details.putAll(wpsDetails(ByteArray(buffer.remaining()).also { buffer.get(it) }))
        }
        return details
    }

    fun stop() {
        active = false
        job?.cancel()
        job = null
        receiver?.let { app.unregisterReceiver(it) }
        receiver = null
    }

    private fun reportError(message: String) {
        lastError = message
    }
}
