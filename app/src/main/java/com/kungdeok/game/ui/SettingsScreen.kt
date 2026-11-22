package com.kungdeok.game.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kungdeok.game.Notifications
import com.kungdeok.game.DawnAlarm
import com.kungdeok.game.overlay.OverlayService
import com.kungdeok.game.overlay.OverlaySettings
import com.kungdeok.game.overlay.OverlaySize

/** 설정 화면 (SPEC 9): 오버레이 켜기/끄기, 크기, 투명도, 알림 */
@Composable
fun SettingsScreen(canDrawOverlays: Boolean, notificationsAllowed: Boolean) {
    val context = LocalContext.current
    val settings = remember { OverlaySettings(context) }
    var enabled by remember { mutableStateOf(settings.enabled && canDrawOverlays) }
    var size by remember { mutableStateOf(settings.size) }
    var opacity by remember { mutableFloatStateOf(settings.opacity) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingCard("화면 위에 띄우기") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("다른 앱을 쓰는 동안 화면 아래를 돌아다녀요", Modifier.weight(1f), fontSize = 14.sp)
                Switch(
                    checked = enabled,
                    enabled = canDrawOverlays,
                    onCheckedChange = {
                        enabled = it
                        settings.enabled = it
                        if (it) {
                            settings.hidden = false
                            OverlayService.start(context)
                        } else {
                            OverlayService.stop(context)
                        }
                    },
                )
            }
            if (!canDrawOverlays) {
                Text(
                    "먼저 '다른 앱 위에 표시' 권한을 허용해 주세요.",
                    color = Color(0xFFB3261E),
                    fontSize = 13.sp,
                )
                Button(onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("권한 허용하러 가기") }
            }
            Text(
                "톡: 머리 위 돌봄 메뉴 · 꾹: 앱 열기 · 드래그: 옮기기\n알림창의 숨기기/보이기 버튼으로 잠깐 숨길 수 있어요.",
                fontSize = 12.sp,
                color = Color.Gray,
            )
        }

        SettingCard("크기") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                OverlaySize.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = size == s,
                        onClick = { size = s; settings.size = s },
                        shape = SegmentedButtonDefaults.itemShape(i, OverlaySize.entries.size),
                    ) { Text(s.label) }
                }
            }
        }

        SettingCard("투명도  ${(opacity * 100).toInt()}%") {
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                onValueChangeFinished = { settings.opacity = opacity },
                valueRange = 0.3f..1f,
            )
        }

        var dawn by remember { mutableStateOf(settings.dawnWake) }
        SettingCard("실제 버릇") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("새벽에 깨우기", fontSize = 14.sp)
                    Text("새벽 5~6시에 밥 달라고 깨우는 알림, 그 시간에 화면을 켜면 우다다", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(checked = dawn, onCheckedChange = {
                    dawn = it
                    settings.dawnWake = it
                    DawnAlarm.schedule(context)
                })
            }
            Text(
                "그 밖의 버릇: 밤에 우다다, 기분 좋으면 꾹꾹이, 박스를 사 두면 박스에 쏙.",
                fontSize = 12.sp,
                color = Color.Gray,
            )
        }

        SettingCard("알림") {
            if (notificationsAllowed) {
                Text("진화 알림이 켜져 있어요.", fontSize = 14.sp)
            } else {
                Text("알림이 꺼져 있으면 진화 알림과 숨기기 버튼이 안 보여요.", fontSize = 14.sp)
                Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("알림 설정 열기") }
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F1))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            content()
        }
    }
}
