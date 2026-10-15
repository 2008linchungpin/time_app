package com.example.timeplanner.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timeplanner.domain.formatClock

@Composable
fun SettingsScreen(vm: PlannerViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var exact by remember { mutableStateOf(vm.canExactAlarm()) }
    var pickTime by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { exact = vm.canExactAlarm() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("設定", style = MaterialTheme.typography.titleLarge)

        ChipRow(
            "全域緩衝：每個任務結束後自動預留",
            listOf("0" to 0, "5 分" to 5, "10 分" to 10, "15 分" to 15),
            s.globalBufferMinutes
        ) { v -> vm.updateSettings { it.copy(globalBufferMinutes = v) } }
        Text(
            "排程或移動任務時，落在緩衝內的下一個任務會自動往後順延（個別任務可在編輯時覆寫）。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ChipRow("新任務預設提前提醒", REMINDER_OPTIONS, s.defaultReminderMinutes) { v ->
            vm.updateSettings { it.copy(defaultReminderMinutes = v) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("每日回顧提醒", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Switch(checked = s.reviewEnabled, onCheckedChange = { c -> vm.updateSettings { it.copy(reviewEnabled = c) } })
            }
            if (s.reviewEnabled) {
                TextButton(onClick = { pickTime = true }) { Text("提醒時間：${formatClock(s.reviewHour * 60 + s.reviewMinute)}") }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("精準鬧鐘", style = MaterialTheme.typography.titleSmall)
            if (exact) {
                Text("已允許：提醒會準時響起 ✔")
            } else {
                Text(
                    "尚未允許：提醒可能延遲幾分鐘。Android 12+ 需要到系統設定手動允許。",
                    color = MaterialTheme.colorScheme.error
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Button(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                        )
                    }) { Text("前往允許") }
                }
            }
            Text(
                "若計時通知被系統回收，請在系統設定把本 App 的電池使用改為「不受限制」。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (pickTime) {
        TimePickDialog(
            s.reviewHour * 60 + s.reviewMinute,
            onConfirm = { m -> vm.updateSettings { it.copy(reviewHour = m / 60, reviewMinute = m % 60) } },
            onDismiss = { pickTime = false }
        )
    }
}
