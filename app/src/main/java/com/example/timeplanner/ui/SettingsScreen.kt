package com.example.timeplanner.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.timeplanner.data.CategoryEntity
import com.example.timeplanner.domain.formatClock

@Composable
fun SettingsScreen(vm: PlannerViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var exact by remember { mutableStateOf(vm.canExactAlarm()) }
    var pickTime by remember { mutableStateOf(false) }
    var pickDefaultStart by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<CategoryEntity?>(null) }
    var recoloring by remember { mutableStateOf<CategoryEntity?>(null) }
    var newColor by remember(categories.size) { mutableStateOf(defaultCategoryColor(categories.size)) }
    var pickNewColor by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { exact = vm.canExactAlarm() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("設定", style = MaterialTheme.typography.titleLarge)

        // ---------- 分類管理 ----------
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("任務分類", style = MaterialTheme.typography.titleSmall)
            Text(
                "順序越上面越重要。新增／編輯任務時可選擇分類。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            categories.forEachIndexed { index, cat ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorDot(cat.colorArgb, 28.dp, Modifier.clickable { recoloring = cat })
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${index + 1}. ${cat.name}",
                        Modifier.weight(1f).padding(end = 4.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    IconButton(
                        onClick = { vm.moveCategoryUp(cat.id) },
                        enabled = index > 0
                    ) { Icon(Icons.Filled.KeyboardArrowUp, "提高重要性") }
                    IconButton(
                        onClick = { vm.moveCategoryDown(cat.id) },
                        enabled = index < categories.lastIndex
                    ) { Icon(Icons.Filled.KeyboardArrowDown, "降低重要性") }
                    TextButton(onClick = { renaming = cat }) { Text("改名") }
                    IconButton(onClick = { vm.deleteCategory(cat.id) }) {
                        Icon(Icons.Filled.Delete, "刪除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Text(
                "點顏色圓點可自訂分類顏色。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorDot(newColor, 32.dp, Modifier.clickable { pickNewColor = true })
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it },
                    label = { Text("新分類名稱") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (newCategory.isNotBlank()) {
                            vm.addCategory(newCategory, newColor)
                            newCategory = ""
                        }
                    },
                    enabled = newCategory.isNotBlank()
                ) { Text("新增") }
            }
        }

        HorizontalDivider()

        ChipRow(
            "全域緩衝：每個任務結束後自動預留",
            listOf("0" to 0, "5 分" to 5, "10 分" to 10, "15 分" to 15),
            s.globalBufferMinutes
        ) { v -> vm.updateSettings { it.copy(globalBufferMinutes = v) } }
        Text(
            "排程或移動任務時，落在緩衝內的下一個任務會自動往後順延（個別任務可在編輯時覆寫）。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ChipRow("新任務預設提前提醒（未特別設定時使用）", REMINDER_OPTIONS, s.defaultReminderMinutes) { v ->
            vm.updateSettings { it.copy(defaultReminderMinutes = v) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("新任務預設開始時間", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { pickDefaultStart = true }) {
                Text("預設：${formatClock(s.defaultStartMinuteOfDay)}")
            }
            Text(
                "新增完整事情且尚未指定時間時，會帶入此預設；選時間預設以 5 分鐘為間距，也可切換自訂。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    if (pickDefaultStart) {
        TimePickDialog(
            s.defaultStartMinuteOfDay,
            onConfirm = { m -> vm.updateSettings { it.copy(defaultStartMinuteOfDay = m) } },
            onDismiss = { pickDefaultStart = false }
        )
    }

    renaming?.let { cat ->
        var name by remember(cat.id) { mutableStateOf(cat.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重新命名分類") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名稱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.renameCategory(cat.id, name)
                        renaming = null
                    }
                ) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } }
        )
    }

    recoloring?.let { cat ->
        ColorPickerDialog(
            initial = cat.colorArgb,
            onConfirm = { vm.setCategoryColor(cat.id, it) },
            onDismiss = { recoloring = null }
        )
    }
    if (pickNewColor) {
        ColorPickerDialog(
            initial = newColor,
            onConfirm = { newColor = it },
            onDismiss = { pickNewColor = false }
        )
    }
}
