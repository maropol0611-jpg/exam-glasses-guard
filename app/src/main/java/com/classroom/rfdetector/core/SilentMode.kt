package com.classroom.rfdetector.core

import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * 측정·감시 중에는 전화, 알림, 알람이 울리지 않게 방해 금지를 켜고 끝나면 되돌린다.
 * 미디어 범주만 남겨 두어 이 앱의 진동 알림은 계속 울린다. 사용자가 방해 금지 권한을 허용해야 쓸 수 있다.
 */
class SilentMode(context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var previous: Pair<Int, NotificationManager.Policy>? = null

    fun allowed(): Boolean = manager.isNotificationPolicyAccessGranted

    fun on() {
        if (previous != null || !allowed()) return
        previous = manager.currentInterruptionFilter to manager.notificationPolicy
        manager.notificationPolicy = NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA, 0, 0)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    fun off() {
        val (filter, policy) = previous ?: return
        previous = null
        if (!allowed()) return
        // Android 15부터는 앱이 켠 방해 금지가 이 앱 전용 모드로 따로 관리되므로, 그 모드만 끄면 된다.
        if (Build.VERSION.SDK_INT >= 35) manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        else {
            manager.notificationPolicy = policy
            manager.setInterruptionFilter(filter)
        }
    }
}
