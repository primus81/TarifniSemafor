package si.semafor.app.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.semafor.app.Estimate
import si.semafor.app.Season
import si.semafor.app.SloTime
import si.semafor.app.TariffCalculator
import si.semafor.app.estimateNote
import si.semafor.app.ui.compose.theme.BodyLineHeight

/**
 * Quick local estimate of the monthly network charge: season, billing power (kW) and consumption (kWh) per block
 * as inputs, the result is updated on every change. The maths is in [TariffCalculator.estimate] (unit tested), the
 * rates come from [si.semafor.app.PriceLists]; nothing is estimated without an official list.
 * The typed texts live here (in the composition), everything else is derived from them.
 */
@Composable
fun NetworkCalculator() {
    val colors = MaterialTheme.colorScheme
    val today = remember { SloTime.today() }
    // Defaults to the season that is active today.
    var season by remember { mutableStateOf(Season.of(today)) }
    var samePower by remember { mutableStateOf(true) }
    var powerAll by remember { mutableStateOf("") }
    val powerBlocks = remember { mutableStateListOf("", "", "", "", "") }
    val energy = remember { mutableStateListOf("", "", "", "", "") }

    val estimate = TariffCalculator.estimate(season, today, samePower, powerAll, powerBlocks, energy)
    val small = MaterialTheme.typography.bodySmall.copy(lineHeight = BodyLineHeight)

    // ---- Inputs ----
    InfoCard {
        Text(
            text = "Hitri izračun omrežnine",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .semantics { heading() }
        )
        Text(
            text = "Okvirni mesečni strošek omrežnine za uporabniško skupino 0. Vnesi dogovorjeno moč in pričakovano porabo.",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight),
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            RadioRow(
                label = "Višja sezona (nov–feb)",
                selected = season == Season.HIGH,
                onClick = { season = Season.HIGH },
                modifier = Modifier.weight(1f),
                fontSize = 13.sp
            )
            RadioRow(
                label = "Nižja sezona (mar–okt)",
                selected = season == Season.LOW,
                onClick = { season = Season.LOW },
                modifier = Modifier.weight(1f),
                fontSize = 13.sp
            )
        }

        Text(
            text = "Dogovorjena obračunska moč (kW)",
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
        )
        SwitchRow(
            label = "Enaka moč za vse bloke",
            checked = samePower,
            onCheckedChange = { samePower = it },
            fontSize = 14.sp
        )
        if (samePower) {
            NumberRow(
                label = "Moč za vse bloke (kW)",
                description = "Dogovorjena obračunska moč v kilovatih, enaka za vse bloke",
                value = powerAll,
                onValueChange = { powerAll = it }
            )
        } else {
            for (b in 1..TariffCalculator.BLOCKS) {
                NumberRow(
                    label = "Blok $b (kW)",
                    description = "Dogovorjena obračunska moč za blok $b v kilovatih",
                    value = powerBlocks[b - 1],
                    onValueChange = { powerBlocks[b - 1] = it }
                )
            }
        }

        Text(
            text = "Pričakovana poraba na mesec (kWh)",
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface,
            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
        )
        for (b in 1..TariffCalculator.BLOCKS) {
            NumberRow(
                label = "Blok $b (kWh)",
                description = "Pričakovana mesečna poraba v bloku $b v kilovatnih urah",
                value = energy[b - 1],
                onValueChange = { energy[b - 1] = it }
            )
        }
    }

    // ---- Result ----
    InfoCard {
        Text(
            text = "Ocena mesečnega stroška",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            modifier = Modifier
                .padding(bottom = 6.dp)
                .semantics { heading() }
        )
        val cost = (estimate as? Estimate.Ready)?.cost
        fun eur(value: java.math.BigDecimal?) = if (value == null) "–" else TariffCalculator.formatEur(value)

        ResultRow("Moč (obračunska moč)", eur(cost?.powerNet))
        ResultRow("Energija (prevzeta energija)", eur(cost?.energyNet))
        Box(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.outlineVariant)
        )
        ResultRow("Skupaj brez DDV", eur(cost?.totalNet))
        ResultRow("DDV (22 %)", eur(cost?.vat))
        // TalkBack reads the new total when it changes, without stealing focus.
        ResultRow("Skupaj z DDV", eur(cost?.totalGross), emphasis = true, liveRegion = true)
        Text(
            text = estimateNote(estimate),
            style = small,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun ResultRow(label: String, value: String, emphasis: Boolean = false, liveRegion: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (emphasis) 6.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (emphasis) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = if (emphasis) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            maxLines = 1,
            modifier = if (liveRegion) Modifier.semantics { this.liveRegion = LiveRegionMode.Polite } else Modifier
        )
    }
}

/** A label on the left, a number field on the right; an invalid text turns the border red and says what to do. */
@Composable
private fun NumberRow(label: String, description: String, value: String, onValueChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val invalid = TariffCalculator.parseAmount(value) == null
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            NumberField(value, onValueChange, description, invalid)
        }
        if (invalid) {
            Text(
                text = "Vnesi število, npr. 5,5",
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * A right-aligned number field (decimal keyboard, "Next" on the keyboard, at most 9 characters). Tapping it selects
 * the whole text, so a new value can simply be typed over the old one.
 */
@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, description: String, invalid: Boolean) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val textStyle = TextStyle(fontSize = 15.sp, color = colors.onSurface, textAlign = TextAlign.End)
    // The field is the only source of changes of [value], so its own text and the parent's text stay in sync.
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    BasicTextField(
        value = field,
        onValueChange = { new ->
            if (new.text.length <= 9) {
                field = new
                if (new.text != value) onValueChange(new.text)
            }
        },
        modifier = Modifier
            .width(120.dp)
            .onFocusChanged { state ->
                if (state.isFocused) field = field.copy(selection = TextRange(0, field.text.length))
            }
            .semantics { contentDescription = description },
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.onSurface),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(colors.background)
                    .border(1.dp, if (invalid) colors.error else colors.outline, shape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (field.text.isEmpty()) {
                    Text(text = "0", style = textStyle.copy(color = colors.onSurfaceVariant))
                }
                inner()
            }
        }
    )
}
