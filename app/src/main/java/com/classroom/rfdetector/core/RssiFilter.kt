package com.classroom.rfdetector.core

import kotlin.math.pow

/** RSSI 평활, 거리 환산, 거리 등급 분류 */
object RssiFilter {
    /** 보정 정보가 없을 때 사용하는 1m 기준 RSSI 가정값. BLE 표준 보정값은 아니다. */
    const val DEFAULT_TX_DBM = -59.0
    /** 실내 경로손실 지수 */
    const val PATH_N = 2.7

    /** 지수 이동평균 */
    fun ema(prev: Double?, sample: Int, alpha: Double = 0.25): Double =
        prev?.let { it + alpha * (sample - it) } ?: sample.toDouble()

    /** 로그 거리 경로손실 모델 */
    fun distanceM(rssi: Double, txDbm: Double = DEFAULT_TX_DBM): Double =
        10.0.pow((txDbm - rssi) / (10 * PATH_N))

    fun classify(distance: Double?): DistanceClass = when {
        distance == null -> DistanceClass.UNKNOWN
        distance < 1.6 -> DistanceClass.VERY_CLOSE
        distance < 3.2 -> DistanceClass.CLOSE
        distance < 8.0 -> DistanceClass.NEAR
        else -> DistanceClass.FAR
    }
}
