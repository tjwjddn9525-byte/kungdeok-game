package com.kungdeok.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kungdeok.game.overlay.OverlayService
import com.kungdeok.game.overlay.OverlaySettings

/** 폰을 다시 켜면 새벽 깨우기 알람을 다시 걸고, 켜 두었던 화면 위 고양이를 다시 띄운다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        DawnAlarm.schedule(context)
        val settings = OverlaySettings(context)
        if (settings.enabled && OverlaySettings.canDrawOverlays(context)) {
            try {
                OverlayService.start(context)
            } catch (_: IllegalStateException) {
                // 허용되지 않으면 다음에 앱을 열 때 켜진다.
            } catch (_: SecurityException) {
            }
        }
    }
}
