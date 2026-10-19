package com.example.timeplanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timeplanner.domain.OverallStats
import com.example.timeplanner.domain.computeOverallStats
import com.example.timeplanner.domain.movingAverage
import com.example.timeplanner.domain.toLocalDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

private val RANGE_OPTIONS: List<Pair<String, Int>> =
    listOf("全部" to 0, "近 90 天" to 90, "近 30 天" to 30, "近 7 天" to 7)

private val MD: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")
private val YMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")

private fun fmtMinutes(min: Int): String =
    if (min >= 60) "${min / 60} 小時 ${min % 60} 分" else "$min 分"

/** 整體回顧：從第一天到今天的累積成果，用折線圖呈現成長。 */
@Composable
fun OverviewScreen(vm: PlannerViewModel, onBack: () -> Unit) {
    val tasks by vm.allTasks.collectAsStateWithLifecycle()
    val entries by vm.allEntries.collectAsStateWithLifecycle()
    val now by rememberNow(false)
    val today = remember(now) { now.toLocalDate() }
    val stats = remember(tasks, entries, today, now) { computeOverallStats(tasks, entries, today, now) }
    var range by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("整體回顧", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val first = stats.days.firstOrNull()?.date ?: today
            Text(
                "從 ${first.format(YMD)} 開始，已經走了 ${stats.days.size} 天",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (stats.totalCompleted == 0 && stats.totalFocusMin == 0) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "完成第一個任務後，這裡就會長出你的成長曲線 🌱",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Insight(stats)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("累計完成", "${stats.totalCompleted} 件", Modifier.weight(1f))
                    StatCard("累計專注", fmtMinutes(stats.totalFocusMin), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        "連續完成", "${stats.currentStreak} 天",
                        Modifier.weight(1f), sub = "最長 ${stats.bestStreak} 天"
                    )
                    StatCard(
                        "單日最多", stats.bestDay?.let { "${it.completed} 件" } ?: "—",
                        Modifier.weight(1f), sub = stats.bestDay?.date?.format(YMD) ?: ""
                    )
                }

                ChipRow("圖表範圍", RANGE_OPTIONS, range) { range = it }

                val n = if (range == 0) stats.days.size else minOf(range, stats.days.size)
                val visible = stats.days.takeLast(n)
                val dates = visible.map { it.date }
                var running = 0
                val cumulativeAll = stats.days.map { running += it.completed; running.toFloat() }
                val cumulative = cumulativeAll.takeLast(n)
                val daily = visible.map { it.completed.toFloat() }
                val focus = visible.map { it.focusMin.toFloat() }

                LineChart(
                    "累計完成任務", "做過的事，一件一件累積起來", dates, cumulative, "件",
                    MaterialTheme.colorScheme.primary
                )
                LineChart(
                    "每日完成任務", "虛線 = 7 日平均", dates, daily, "件",
                    MaterialTheme.colorScheme.tertiary,
                    average = if (n >= 14) movingAverage(daily, 7) else null
                )
                LineChart(
                    "每日專注時間", "虛線 = 7 日平均", dates, focus, "分鐘",
                    MaterialTheme.colorScheme.secondary,
                    average = if (n >= 14) movingAverage(focus, 7) else null
                )
                LineChart(
                    "預估準度", "實際 ÷ 預估；越貼近 100% 代表越會抓時間", dates,
                    visible.map { it.accuracyPct?.toFloat() }, "%",
                    MaterialTheme.colorScheme.error, baseline = 100f
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Insight(stats: OverallStats) {
    val last7 = stats.days.takeLast(7).sumOf { it.completed }
    val prev7 = stats.days.dropLast(7).takeLast(7).sumOf { it.completed }
    val text = when {
        stats.days.size >= 14 && last7 > prev7 -> "🔥 近 7 天完成 $last7 件，比前 7 天多 ${last7 - prev7} 件，繼續保持！"
        stats.days.size >= 14 && last7 == prev7 && last7 > 0 -> "💪 近 7 天完成 $last7 件，和前 7 天一樣穩定。"
        last7 > 0 -> "✨ 近 7 天完成 $last7 件，每一件都算數。"
        stats.totalCompleted > 0 -> "🌱 你已經完成過 ${stats.totalCompleted} 件事，今天再加一件吧！"
        else -> "⏱ 已經累積 ${fmtMinutes(stats.totalFocusMin)} 的專注時間。"
    }
    Card(Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, sub: String = "") {
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun niceMax(v: Float): Float {
    if (v <= 1f) return 1f
    val p = 10.0.pow(floor(log10(v.toDouble()))).toFloat()
    val n = v / p
    val m = when {
        n <= 1f -> 1f
        n <= 2f -> 2f
        n <= 5f -> 5f
        else -> 10f
    }
    return m * p
}

private fun fmtValue(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)

/**
 * 簡易折線圖（不依賴外部圖表套件）。values 與 dates 一一對應，null = 當天沒資料（線會跳過）。
 * 點一下或左右滑動可以看某一天的數值。
 */
@Composable
private fun LineChart(
    title: String,
    subtitle: String,
    dates: List<LocalDate>,
    values: List<Float?>,
    unit: String,
    color: Color,
    average: List<Float>? = null,
    baseline: Float? = null
) {
    val n = values.size
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val dotFill = MaterialTheme.colorScheme.surface
    val top = niceMax(max(max(values.filterNotNull().maxOrNull() ?: 0f, baseline ?: 0f), 1f))

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val sel = selected
            val readout = when {
                n == 0 -> "沒有資料"
                sel != null && sel in 0 until n ->
                    "${dates[sel].format(MD)}：" + (values[sel]?.let { "${fmtValue(it)} $unit" } ?: "沒有資料")
                else -> {
                    val last = values.lastOrNull()
                    "今天：" + (last?.let { "${fmtValue(it)} $unit" } ?: "沒有資料")
                }
            }
            Text(readout, style = MaterialTheme.typography.titleSmall, color = color)

            Box(Modifier.fillMaxWidth().height(160.dp)) {
                Canvas(
                    Modifier.fillMaxSize()
                        .pointerInput(n) {
                            detectTapGestures { o ->
                                if (n > 0) selected = if (n == 1) 0 else (o.x / size.width * (n - 1)).roundToInt().coerceIn(0, n - 1)
                            }
                        }
                        .pointerInput(n) {
                            detectHorizontalDragGestures { change, _ ->
                                if (n > 0) selected =
                                    if (n == 1) 0 else (change.position.x / size.width * (n - 1)).roundToInt().coerceIn(0, n - 1)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val padV = 8.dp.toPx()
                    val chartH = h - padV * 2
                    fun xAt(i: Int) = if (n <= 1) w / 2f else w * i / (n - 1)
                    fun yAt(v: Float) = padV + chartH * (1f - (v / top).coerceIn(0f, 1f))

                    // 格線：0 / 1/2 / 最大
                    for (k in 0..2) {
                        val y = yAt(top * k / 2f)
                        drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                    }
                    baseline?.let {
                        val y = yAt(it)
                        drawLine(
                            color.copy(alpha = 0.6f), Offset(0f, y), Offset(w, y), strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                        )
                    }

                    val pts = values.mapIndexedNotNull { i, v -> v?.let { Offset(xAt(i), yAt(it)) } }
                    if (pts.isNotEmpty()) {
                        val line = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            for (p in pts.drop(1)) lineTo(p.x, p.y)
                        }
                        if (pts.size > 1) {
                            val fill = Path().apply {
                                moveTo(pts[0].x, h - padV)
                                for (p in pts) lineTo(p.x, p.y)
                                lineTo(pts.last().x, h - padV)
                                close()
                            }
                            drawPath(fill, color.copy(alpha = 0.12f))
                        }
                        drawPath(
                            line, color,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        if (n <= 31) pts.forEach { p ->
                            drawCircle(color, 3.dp.toPx(), p)
                        }
                    }

                    average?.let { avg ->
                        if (avg.size > 1) {
                            val p = Path().apply {
                                moveTo(xAt(0), yAt(avg[0]))
                                for (i in 1 until avg.size) lineTo(xAt(i), yAt(avg[i]))
                            }
                            drawPath(
                                p, color.copy(alpha = 0.7f),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f))
                                )
                            )
                        }
                    }

                    selected?.takeIf { it in 0 until n }?.let { i ->
                        val x = xAt(i)
                        drawLine(color.copy(alpha = 0.5f), Offset(x, padV), Offset(x, h - padV), strokeWidth = 1.5.dp.toPx())
                        values[i]?.let {
                            drawCircle(dotFill, 6.dp.toPx(), Offset(x, yAt(it)))
                            drawCircle(color, 4.5.dp.toPx(), Offset(x, yAt(it)))
                        }
                    }
                }
                Text(
                    "${fmtValue(top)} $unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
            if (n > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val small = MaterialTheme.typography.labelSmall
                    val c = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(dates.first().format(MD), style = small, color = c)
                    if (n >= 5) Text(dates[n / 2].format(MD), style = small, color = c)
                    Text(dates.last().format(MD), style = small, color = c)
                }
            }
        }
    }
}
