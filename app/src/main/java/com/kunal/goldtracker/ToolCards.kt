package com.kunal.goldtracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val KaratOptions = listOf(24 to "24K", 22 to "22K", 18 to "18K", 14 to "14K")

// Rate per gram before GST: GST is charged on the bill, and isn't part of what gold is worth.
private fun ratePerGram(spot: Double, settings: PriceSettings, karat: Int): Double =
    GoldRepository.toIndianPrices(spot, settings.copy(includeGst = false)).forKarat(karat)

/** Estimates a jewellery bill: gold value + making charges + 3% GST on both. */
@Composable
internal fun CalculatorCard(spot: Double, settings: PriceSettings) {
    var weightText by remember { mutableStateOf("") }
    var makingText by remember { mutableStateOf("12") }
    var karat by remember { mutableIntStateOf(22) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Jewellery price calculator", style = MaterialTheme.typography.titleMedium)
            NumberField("Weight (g)", weightText) { weightText = it }
            OptionRow("Purity", KaratOptions, karat) { karat = it }
            NumberField("Making charges %", makingText) { makingText = it }

            val weight = parseNumber(weightText)
            val making = parseNumber(makingText) ?: 0.0
            if (spot > 0 && weight != null && weight > 0) {
                val gold = ratePerGram(spot, settings, karat) * weight
                val makingAmount = gold * making / 100
                val gst = (gold + makingAmount) * 0.03
                AmountRow("Gold value", gold)
                AmountRow("Making charges", makingAmount)
                AmountRow("GST 3%", gst)
                HorizontalDivider()
                AmountRow("Estimated total", gold + makingAmount + gst, bold = true)
                Text(
                    "Making charges differ by jeweller and design; ask the store for their %.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Gold the user owns, valued at today's rate, with profit or loss against what they paid. */
@Composable
internal fun HoldingsCard(spot: Double, settings: PriceSettings) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(Holdings.load(context)) }
    var nameText by remember { mutableStateOf("") }
    var gramsText by remember { mutableStateOf("") }
    var paidText by remember { mutableStateOf("") }
    var karat by remember { mutableIntStateOf(22) }

    fun update(newItems: List<Holding>) {
        items = newItems
        Holdings.save(context, newItems)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("My gold", style = MaterialTheme.typography.titleMedium)

            if (items.isEmpty()) {
                Text("Add the gold you own to see what it's worth today.", style = MaterialTheme.typography.bodySmall)
            } else if (spot > 0) {
                items.forEach { h ->
                    val value = ratePerGram(spot, settings, h.karat) * h.grams
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.name.ifBlank { "Gold" }, style = MaterialTheme.typography.bodyLarge)
                            Text("${h.grams} g · ${h.karat}K · paid ${formatInr(h.paid)}", style = MaterialTheme.typography.bodySmall)
                            if (h.paid > 0) ChangeText(value - h.paid, h.paid, "")
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatInr(value), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { update(items.filter { it.id != h.id }) }) { Text("Remove") }
                        }
                    }
                }
                HorizontalDivider()
                val totalValue = items.sumOf { ratePerGram(spot, settings, it.karat) * it.grams }
                val totalPaid = items.sumOf { it.paid }
                AmountRow("Total value (${"%.2f".format(items.sumOf { it.grams })} g)", totalValue, bold = true)
                if (totalPaid > 0) ChangeText(totalValue - totalPaid, totalPaid, "overall")
                Text(
                    "Valued at today's rate before GST. Jewellers deduct some amount when buying back.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text("Add gold", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text("Name (e.g. Chain, Coin)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField("Weight (g)", gramsText) { gramsText = it }
            OptionRow("Purity", KaratOptions, karat) { karat = it }
            NumberField("Amount paid (₹, optional)", paidText) { paidText = it }
            OutlinedButton(onClick = {
                val grams = parseNumber(gramsText)
                if (grams != null && grams > 0) {
                    update(items + Holding(System.currentTimeMillis(), nameText.trim(), grams, karat, parseNumber(paidText) ?: 0.0))
                    nameText = ""
                    gramsText = ""
                    paidText = ""
                }
            }) {
                Text("Add")
            }
        }
    }
}

@Composable
private fun AmountRow(label: String, amount: Double, bold: Boolean = false) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = weight)
        Text(formatInr(amount), fontWeight = weight)
    }
}
