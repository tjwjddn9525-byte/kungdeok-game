package com.kungdeok.game

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kungdeok.game.model.CatId
import com.kungdeok.game.model.Form

object Notifications {
    const val CHANNEL_EVOLUTION = "evolution"
    const val CHANNEL_OVERLAY = "overlay"
    const val CHANNEL_HABIT = "habit"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EVOLUTION, "진화 알림", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "진화할 수 있을 때 알려 줘요" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_HABIT, "새벽에 깨우기", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "새벽 5~6시에 고양이가 밥 달라고 깨워요 (설정에서 끌 수 있어요)" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_OVERLAY, "화면 위 고양이", NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = "화면 위에 고양이를 띄우는 동안 보이는 알림 (숨기기/보이기 버튼)"
                    setShowBadge(false)
                },
        )
    }

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun evolutionReady(context: Context, id: CatId, stage: Int) {
        if (!canPost(context)) return
        val form = Form.of(id, stage)
        val n = NotificationCompat.Builder(context, CHANNEL_EVOLUTION)
            .setSmallIcon(R.drawable.ic_stat_paw)
            .setContentTitle("진화할 수 있어요!")
            .setContentText("${id.catName}(${form.monName})이 진화할 준비가 됐어요. 눌러서 진화시켜 주세요.")
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(100 + id.ordinal, n)
        } catch (_: SecurityException) {
        }
    }
}
