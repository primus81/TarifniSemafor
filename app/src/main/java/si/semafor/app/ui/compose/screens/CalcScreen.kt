package si.semafor.app.ui.compose.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import si.semafor.app.Links
import si.semafor.app.ui.compose.components.InfoCard
import si.semafor.app.ui.compose.components.LinkText
import si.semafor.app.ui.compose.components.NetworkCalculator
import si.semafor.app.ui.compose.components.PrimaryButton
import si.semafor.app.ui.compose.components.ScreenHeader
import si.semafor.app.ui.compose.components.ScreenScaffold
import si.semafor.app.ui.compose.theme.AppTheme
import si.semafor.app.ui.compose.theme.BodyLineHeight

private const val HELP_EMAIL = "primerjalnik@agen-rs.si"

/**
 * The "Kalkulator" screen: the quick local network-charge estimate on top, below it the information about the
 * Energy Agency comparison tool and its external link. The host supplies callbacks only (no Activity reference).
 */
@Composable
fun CalcScreen(onNavigateHome: () -> Unit, onOpenUrl: (String) -> Unit, onSendMail: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val body = MaterialTheme.typography.bodyMedium.copy(lineHeight = BodyLineHeight)
    val small = MaterialTheme.typography.bodySmall

    @Composable
    fun InfoSection(title: String, text: String, extra: @Composable () -> Unit = {}) {
        InfoCard {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(text = text, style = body, color = colors.onSurface)
            extra()
        }
    }

    ScreenScaffold {
        ScreenHeader(
            title = "Kalkulator",
            subtitle = "Primerjalnik stroškov oskrbe",
            onBack = onNavigateHome
        )

        // Native quick estimate first; the existing Energy Agency comparison stays below it.
        NetworkCalculator()

        Text(
            text = "Primerjalnik ponudb dobaviteljev",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onBackground,
            modifier = Modifier
                .padding(top = 12.dp, bottom = 6.dp)
                .semantics { heading() }
        )

        InfoSection(
            "Kaj je to?",
            "Brezplačno orodje Agencije za energijo, s katerim gospodinjski in mali poslovni odjemalci primerjajo ponudbe dobaviteljev elektrike in zemeljskega plina. Orodje obstaja od odprtja trga leta 2007, prenovljena različica pa zajema tudi ponudbe z dinamičnimi cenami elektrike."
        )
        InfoSection(
            "Kako deluje?",
            "Vneseš svojo letno porabo elektrike (ali plina) v kWh in izbereš lokalno skupnost. Druga polja so neobvezna in služijo natančnejši prilagoditvi izračuna tvoji porabi. Za dinamične cene so na voljo različni urni profili odjema, ki bolje odražajo porabo po urah."
        )
        InfoSection(
            "Katere ponudbe so vključene?",
            "V primerjavo so zajeti vsi ceniki, ki so jih dobavitelji vnesli v aplikacijo in jih je potrdila Agencija za energijo. Za ažurnost in pravilnost podatkov so odgovorni dobavitelji, Agencija pa opravi osnovno preverjanje. Seznam dobaviteljev najdeš v samem kalkulatorju (»Prikaz tržnih udeležencev«)."
        )
        InfoSection(
            "Osebni podatki",
            "Kalkulator deluje na spletni strani Agencije za energijo, ne v aplikaciji Tarifni semafor. Aplikacija tvojih vnosov ne vidi, ne zbira in ne shranjuje. Z vpisom letnih agregatov porabe v kalkulator soglašaš z njihovo obdelavo s strani Agencije za namen primerjave ponudb. Pred uporabo preberi pravila in pogoje uporabe; če se z njimi ne strinjaš, podatkov ne vnašaj."
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                LinkText("Pravila in pogoji uporabe") { onOpenUrl(Links.CALC_TERMS) }
                LinkText("Splošne informacije o primerjalniku") { onOpenUrl(Links.CALC_INFO) }
            }
        }

        PrimaryButton("Odpri kalkulator") { onOpenUrl(Links.CALC_URL) }

        Text(
            text = "Odpre se v brskalniku (aplikacije.agen-rs.si).",
            style = small,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = buildAnnotatedString {
                append("Pomoč pri uporabi kalkulatorja: ")
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "mail",
                        styles = TextLinkStyles(
                            SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)
                        )
                    ) { onSendMail(HELP_EMAIL) }
                ) { append(HELP_EMAIL) }
            },
            style = small,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp)
        )

        Text(
            text = "Povzeto po opisu na strani kalkulatorja (stanje: november 2026). Aplikacija Tarifni semafor ni uradna aplikacija Agencije za energijo.",
            style = small,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF161C28)
@Composable
private fun CalcScreenPreview() {
    AppTheme { CalcScreen(onNavigateHome = {}, onOpenUrl = {}, onSendMail = {}) }
}
