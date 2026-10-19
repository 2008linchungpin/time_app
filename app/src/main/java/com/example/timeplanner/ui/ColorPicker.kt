package com.example.timeplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/** 分類預設色盤（新增分類時依序取用；也是色彩選擇器裡的快速選色）。 */
val CATEGORY_PALETTE: List<Int> = listOf(
    0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00ACC1,
    0xFF1E88E5, 0xFF5E35B1, 0xFFD81B60, 0xFF6D4C41, 0xFF757575
).map { it.toInt() }

fun defaultCategoryColor(index: Int): Int = CATEGORY_PALETTE[index.mod(CATEGORY_PALETTE.size)]

@Composable
fun ColorDot(argb: Int, size: androidx.compose.ui.unit.Dp = 24.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(Color(argb))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
    )
}

private fun argbToHsv(argb: Int): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return hsv
}

/** 自訂顏色：色相 / 飽和度 / 亮度滑桿、#RRGGBB 輸入、快速色盤。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val hsv0 = remember { argbToHsv(initial) }
    var hue by remember { mutableFloatStateOf(hsv0[0]) }
    var sat by remember { mutableFloatStateOf(hsv0[1]) }
    var value by remember { mutableFloatStateOf(hsv0[2]) }
    val argb = Color.hsv(hue, sat, value).toArgb()
    var hex by remember { mutableStateOf("%06X".format(initial and 0xFFFFFF)) }

    fun applyArgb(c: Int) {
        val h = argbToHsv(c)
        hue = h[0]; sat = h[1]; value = h[2]
        hex = "%06X".format(c and 0xFFFFFF)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇顏色") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)).background(Color(argb))
                )
                Text("色相", style = MaterialTheme.typography.labelMedium)
                Slider(value = hue, onValueChange = { hue = it; hex = "%06X".format(Color.hsv(it, sat, value).toArgb() and 0xFFFFFF) }, valueRange = 0f..360f)
                Text("飽和度", style = MaterialTheme.typography.labelMedium)
                Slider(value = sat, onValueChange = { sat = it; hex = "%06X".format(Color.hsv(hue, it, value).toArgb() and 0xFFFFFF) }, valueRange = 0f..1f)
                Text("亮度", style = MaterialTheme.typography.labelMedium)
                Slider(value = value, onValueChange = { value = it; hex = "%06X".format(Color.hsv(hue, sat, it).toArgb() and 0xFFFFFF) }, valueRange = 0f..1f)
                OutlinedTextField(
                    value = hex,
                    onValueChange = { raw ->
                        val t = raw.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        hex = t
                        if (t.length == 6) applyArgb((0xFF000000L or t.toLong(16)).toInt())
                    },
                    prefix = { Text("#") },
                    label = { Text("色碼（RRGGBB）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("快速選色", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CATEGORY_PALETTE.forEach { c ->
                        ColorDot(c, 32.dp, Modifier.clickable { applyArgb(c) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(argb); onDismiss() }) { Text("確定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
