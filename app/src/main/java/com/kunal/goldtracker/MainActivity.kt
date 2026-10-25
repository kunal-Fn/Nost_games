package com.kunal.goldtracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Alerts.createChannel(this)
        val prefs = Prefs(this)
        // Runs even with alerts off: these checks also build the 1D chart.
        PriceCheckWorker.schedule(this, prefs.intervalMinutes)

        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF8A6D12))) {
                Surface(Modifier.fillMaxSize()) {
                    GoldScreen(prefs)
                }
            }
        }
    }
}

@Composable
private fun GoldScreen(prefs: Prefs) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var spot by remember { mutableDoubleStateOf(prefs.lastSpotInrPerGram) }
    var updated by remember { mutableLongStateOf(prefs.lastUpdated) }
    var source by remember { mutableStateOf(prefs.lastSource) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var prevClose by remember { mutableDoubleStateOf(prefs.lastPrevClose) }
    var range by remember { mutableStateOf(TrendRange.WEEK) }
    var trend by remember { mutableStateOf<List<PricePoint>>(emptyList()) }
    var trendLoading by remember { mutableStateOf(false) }

    var priceSettings by remember { mutableStateOf(prefs.priceSettings) }
    var dutyText by remember { mutableStateOf(priceSettings.dutyPercent.toString()) }
    var adjustText by remember { mutableStateOf(priceSettings.adjustPercent.toString()) }
    var includeGst by remember { mutableStateOf(priceSettings.includeGst) }

    var alertsOn by remember { mutableStateOf(prefs.alertsEnabled) }
    var upperText by remember { mutableStateOf(limitText(prefs.upperLimit)) }
    var lowerText by remember { mutableStateOf(limitText(prefs.lowerLimit)) }
    var karat by remember { mutableIntStateOf(prefs.alertKarat) }
    var interval by remember { mutableIntStateOf(prefs.intervalMinutes) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) error = "Notifications are blocked, so alerts won't show. Allow them in system settings."
    }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                GoldRepository.refresh(context, prefs)
                spot = prefs.lastSpotInrPerGram
                updated = prefs.lastUpdated
                source = prefs.lastSource
                prevClose = prefs.lastPrevClose
            } catch (e: Exception) {
                error = "Could not fetch price: ${e.message}"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (System.currentTimeMillis() - prefs.lastUpdated > 5 * 60_000) refresh()
    }

    LaunchedEffect(range, updated) {
        trendLoading = true
        trend = History.load(context, range)
        trendLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("India Gold Price", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (spot <= 0) {
                    Text(if (loading) "Loading…" else "No price yet. Tap Refresh.")
                } else {
                    val prices = GoldRepository.toIndianPrices(spot, priceSettings)
                    val prev = prevClose.takeIf { it > 0 }?.let { GoldRepository.toIndianPrices(it, priceSettings) }
                    PriceRow("24K", prices.k24, prev?.k24)
                    PriceRow("22K", prices.k22, prev?.k22)
                    PriceRow("18K", prices.k18, prev?.k18)
                    PriceRow("14K", prices.k14, prev?.k14)
                    Text(
                        "Updated ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(updated))}\nSource: $source",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { refresh() }, enabled = !loading) {
                    Text(if (loading) "Refreshing…" else "Refresh")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("24K trend (₹ per 10 g)", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TrendRange.entries.forEach { r ->
                        val pad = PaddingValues(0.dp)
                        if (r == range) {
                            Button(onClick = {}, Modifier.weight(1f), contentPadding = pad) { Text(r.label) }
                        } else {
                            OutlinedButton(onClick = { range = r }, Modifier.weight(1f), contentPadding = pad) { Text(r.label) }
                        }
                    }
                }
                // End the daily series at the live price so the chart and the card agree.
                val points = if (range != TrendRange.DAY && spot > 0 && trend.isNotEmpty() && updated > trend.last().time) {
                    trend + PricePoint(updated, spot)
                } else {
                    trend
                }
                val values = points.map { it.time to GoldRepository.toIndianPrices(it.spotInrPerGram, priceSettings).k24 * 10 }
                when {
                    trendLoading && values.isEmpty() -> Text("Loading…")
                    values.size < 2 -> Text(
                        if (range == TrendRange.DAY) "The day chart fills in as the app checks the price in the background."
                        else "No history available right now. Check your connection.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> {
                        val dateFormat = if (range == TrendRange.DAY) DateFormat.getTimeInstance(DateFormat.SHORT)
                            else DateFormat.getDateInstance(DateFormat.MEDIUM)
                        TrendChart(values, range.label, dateFormat)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(dateFormat.format(Date(values.first().first)), style = MaterialTheme.typography.bodySmall)
                            Text(dateFormat.format(Date(values.last().first)), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "High ${formatInr(values.maxOf { it.second })} · Low ${formatInr(values.minOf { it.second })}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        CalculatorCard(spot, priceSettings)
        HoldingsCard(spot, priceSettings)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Price alert", style = MaterialTheme.typography.titleMedium)
                SwitchRow("Notify me when price crosses a limit", alertsOn) { alertsOn = it }
                OptionRow("Purity", listOf(24 to "24K", 22 to "22K", 18 to "18K", 14 to "14K"), karat) { karat = it }
                NumberField("Alert above (₹ per 10 g)", upperText) { upperText = it }
                NumberField("Alert below (₹ per 10 g)", lowerText) { lowerText = it }
                OptionRow("Check every", listOf(15 to "15 min", 30 to "30 min", 60 to "1 hr"), interval) { interval = it }
                Button(onClick = {
                    val upper = parseNumber(upperText) ?: 0.0
                    val lower = parseNumber(lowerText) ?: 0.0
                    if (alertsOn && upper <= 0 && lower <= 0) {
                        error = "Set at least one limit for the alert."
                        return@Button
                    }
                    prefs.alertsEnabled = alertsOn
                    prefs.upperLimit = upper
                    prefs.lowerLimit = lower
                    prefs.alertKarat = karat
                    prefs.intervalMinutes = interval
                    prefs.upperFired = false
                    prefs.lowerFired = false
                    if (alertsOn) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        Alerts.check(context, prefs)
                    }
                    PriceCheckWorker.schedule(context, interval)
                    error = null
                    Toast.makeText(context, "Alert saved", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save alert")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Price calculation", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Estimated from the international spot price plus import duty (and GST if on). " +
                        "Jeweller and city rates differ, so use Adjustment % to match the rate you see locally.",
                    style = MaterialTheme.typography.bodySmall,
                )
                NumberField("Import duty %", dutyText) { dutyText = it }
                NumberField("Adjustment % (premium, can be negative)", adjustText) { adjustText = it }
                SwitchRow("Include 3% GST", includeGst) { includeGst = it }
                OutlinedButton(onClick = {
                    val newSettings = PriceSettings(
                        dutyPercent = parseNumber(dutyText) ?: 15.0,
                        adjustPercent = parseNumber(adjustText) ?: 0.0,
                        includeGst = includeGst,
                    )
                    prefs.priceSettings = newSettings
                    priceSettings = newSettings
                    PriceWidget.updateAll(context)
                    Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save settings")
                }
            }
        }
    }
}

private val RiseColor = Color(0xFF2E7D32)
private val FallColor = Color(0xFFC62828)

@Composable
private fun PriceRow(label: String, perGram: Double, prevPerGram: Double?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleLarge)
        Column(horizontalAlignment = Alignment.End) {
            Text("${formatInr(perGram * 10)} / 10 g", style = MaterialTheme.typography.titleMedium)
            Text("${formatInr(perGram)} / g", style = MaterialTheme.typography.bodySmall)
            if (prevPerGram != null && prevPerGram > 0) {
                ChangeText((perGram - prevPerGram) * 10, prevPerGram * 10, "vs prev. close")
            }
        }
    }
}

/** e.g. "▲ ₹1,250 (+0.98%) vs prev. close", green for a rise and red for a fall. */
@Composable
internal fun ChangeText(change: Double, base: Double, suffix: String) {
    val rising = change >= 0
    val arrow = if (rising) "▲" else "▼"
    val sign = if (rising) "+" else "-"
    val percent = "%.2f".format(abs(change) / base * 100)
    Text(
        "$arrow ${formatInr(abs(change))} ($sign$percent%) $suffix",
        color = if (rising) RiseColor else FallColor,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Line chart; tap or slide sideways to put a marker on a point and read its price. */
@Composable
private fun TrendChart(values: List<Pair<Long, Double>>, periodLabel: String, dateFormat: DateFormat) {
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val first = values.first().second
    val last = values.last().second
    val color = if (last >= first) RiseColor else FallColor
    val t0 = values.first().first
    val spanT = (values.last().first - t0).coerceAtLeast(1L).toFloat()

    // Two lines in both states so the chart doesn't jump while dragging.
    val point = selected?.let { values[it] }
    if (point == null) {
        ChangeText(last - first, first, "over $periodLabel")
        Text("Tap or slide on the chart to see prices", style = MaterialTheme.typography.bodySmall)
    } else {
        Text(
            "${formatInr(point.second)} · ${dateFormat.format(Date(point.first))}",
            style = MaterialTheme.typography.titleSmall,
        )
        ChangeText(point.second - first, first, "from start of $periodLabel")
    }

    fun nearestIndex(xPx: Float, widthPx: Int): Int {
        val t = t0 + (xPx / widthPx).coerceIn(0f, 1f) * spanT
        return values.indices.minBy { abs(values[it].first - t) }
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(values) {
                detectTapGestures { selected = nearestIndex(it.x, size.width) }
            }
            .pointerInput(values) {
                detectHorizontalDragGestures(
                    onDragStart = { selected = nearestIndex(it.x, size.width) },
                ) { change, _ ->
                    change.consume()
                    selected = nearestIndex(change.position.x, size.width)
                }
            }
    ) {
        val inset = 4.dp.toPx()
        val chartHeight = size.height - 2 * inset
        val minV = values.minOf { it.second }
        val spanV = (values.maxOf { it.second } - minV).takeIf { it > 0 } ?: 1.0
        fun x(t: Long) = (t - t0) / spanT * size.width
        fun y(v: Double) = inset + chartHeight - ((v - minV) / spanV).toFloat() * chartHeight

        val line = Path()
        values.forEachIndexed { i, (t, v) ->
            if (i == 0) line.moveTo(x(t), y(v)) else line.lineTo(x(t), y(v))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(values.last().first), size.height)
            lineTo(x(t0), size.height)
            close()
        }
        drawPath(area, color.copy(alpha = 0.15f))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx()))

        point?.let { (t, v) ->
            val px = x(t)
            drawLine(Color.Gray, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1.dp.toPx())
            drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(px, y(v)))
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(px, y(v)))
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun OptionRow(label: String, options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            options.forEach { (value, text) ->
                RadioButton(selected = value == selected, onClick = { onSelect(value) })
                Text(text, Modifier.padding(end = 12.dp))
            }
        }
    }
}

@Composable
internal fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun parseNumber(text: String): Double? = text.replace(",", "").trim().toDoubleOrNull()

private fun limitText(value: Double): String = if (value > 0) "%.0f".format(value) else ""
