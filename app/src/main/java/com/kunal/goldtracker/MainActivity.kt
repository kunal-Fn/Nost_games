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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Alerts.createChannel(this)
        val prefs = Prefs(this)
        if (prefs.alertsEnabled) PriceCheckWorker.schedule(this, prefs.intervalMinutes)

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
            } catch (e: Exception) {
                error = "Could not fetch price: ${e.message}"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (System.currentTimeMillis() - prefs.lastUpdated > 5 * 60_000) refresh()
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
                    PriceRow("24K", prices.k24)
                    PriceRow("22K", prices.k22)
                    PriceRow("18K", prices.k18)
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
                Text("Price alert", style = MaterialTheme.typography.titleMedium)
                SwitchRow("Notify me when price crosses a limit", alertsOn) { alertsOn = it }
                OptionRow("Purity", listOf(24 to "24K", 22 to "22K"), karat) { karat = it }
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
                        PriceCheckWorker.schedule(context, interval)
                        Alerts.check(context, prefs)
                    } else {
                        PriceCheckWorker.cancel(context)
                    }
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
                    "Estimated from the international spot price plus import duty and GST. " +
                        "Jeweller and city rates differ, so use Adjustment % to match the rate you see locally.",
                    style = MaterialTheme.typography.bodySmall,
                )
                NumberField("Import duty %", dutyText) { dutyText = it }
                NumberField("Adjustment % (premium, can be negative)", adjustText) { adjustText = it }
                SwitchRow("Include 3% GST", includeGst) { includeGst = it }
                OutlinedButton(onClick = {
                    val newSettings = PriceSettings(
                        dutyPercent = parseNumber(dutyText) ?: 6.0,
                        adjustPercent = parseNumber(adjustText) ?: 0.0,
                        includeGst = includeGst,
                    )
                    prefs.priceSettings = newSettings
                    priceSettings = newSettings
                    Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save settings")
                }
            }
        }
    }
}

@Composable
private fun PriceRow(label: String, perGram: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleLarge)
        Column(horizontalAlignment = Alignment.End) {
            Text("${formatInr(perGram * 10)} / 10 g", style = MaterialTheme.typography.titleMedium)
            Text("${formatInr(perGram)} / g", style = MaterialTheme.typography.bodySmall)
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
private fun OptionRow(label: String, options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            options.forEach { (value, text) ->
                RadioButton(selected = value == selected, onClick = { onSelect(value) })
                Text(text, Modifier.padding(end = 12.dp))
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun parseNumber(text: String): Double? = text.replace(",", "").trim().toDoubleOrNull()

private fun limitText(value: Double): String = if (value > 0) "%.0f".format(value) else ""
