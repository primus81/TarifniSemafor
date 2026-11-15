package si.semafor.app.mojelektro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The JSON below is SYNTHETIC test data written from the field names of the official OpenAPI description
 * (mojelektro-openapi.yaml 1.0); all identifiers and values are made up.
 */
class MojElektroTest {

    private val meteringPointJson = """
        {
          "identifikator": {"enotniIdentifikatorMerilnegaMesta": "3-1234567", "gsrn": "383000000000000001"},
          "merilneTocke": [{"gsrn": "383000000000000002", "vrsta": "OMTO"}, {"vrsta": "MTP"}],
          "naziv": "Test",
          "lastnik": {"naziv": "Ime Priimek", "davcnaSt": "12345678", "naslov": "Ulica 1"},
          "pogodbeniPodatki": {"obstoj15MinutneMeritve": true, "daljinskoCitanje": true}
        }
    """.trimIndent()

    @Test
    fun meteringPoint_parsesTechnicalSubset() {
        val p = MojElektroParser.meteringPoint(meteringPointJson)
        assertEquals("3-1234567", p.eimm)
        assertEquals("383000000000000001", p.gsrn)
        // The entry without GSRN is skipped.
        assertEquals(listOf(MeasurementPointRef("383000000000000002", "OMTO")), p.measurementPoints)
        assertEquals(true, p.has15MinuteData)
        assertEquals(true, p.remoteReading)
        // Personal data of the owner is not part of the model at all.
        assertFalse(p.toString().contains("Priimek"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun meteringPoint_withoutEimm_isInvalid() {
        MojElektroParser.meteringPoint("""{"identifikator": {"gsrn": "1"}}""")
    }

    @Test
    fun measurementPoint_parsesAgreedPowerPerBlock() {
        val json = """
            {"gsrn": "383000000000000002", "naziv": "MT", "vrsta": "OMTO", "uporabniskaSkupina": "0",
             "dogovorjeneMoci": [{"datumOd": "2024-10-01T00:00:00+02:00", "datumDo": null, "veljavnost": true,
               "casovniBlok1": "7.5", "casovniBlok2": "7.5", "casovniBlok3": "8", "casovniBlok4": "8.0"}]}
        """.trimIndent()
        val m = MojElektroParser.measurementPoint(json)
        assertEquals("0", m.userGroup)
        val power = m.agreedPowers.single()
        assertEquals("2024-10-01T00:00:00+02:00", power.validFrom)
        assertNull(power.validTo)
        assertEquals(true, power.valid)
        assertEquals(5, power.blockKw.size)
        assertEquals(BigDecimal("7.5"), power.blockKw[0])
        assertEquals(BigDecimal("8.0"), power.blockKw[3])
        assertNull(power.blockKw[4]) // not sent
    }

    @Test
    fun meterReadings_parsesSeriesAndQualities() {
        val json = """
            {"usagePoint": "3-1234567", "messageCreated": "2024-01-02T10:00:00+01:00",
             "intervalBlocks": [{"readingType": "32.0.2.4.1.2.12.0.0.0.0.0.0.0.0.3.72.0", "intervalReadings": [
               {"timestamp": "2024-01-01T00:15:00+01:00", "value": "0.125"},
               {"timestamp": "2024-01-01T00:30:00+01:00", "value": "0.250",
                "readingQualities": [{"readingQualityType": "X"}]},
               {"timestamp": "not a date", "value": "1"}
             ]}]}
        """.trimIndent()
        val r = MojElektroParser.meterReadings(json)
        assertEquals("3-1234567", r.usagePoint)
        val series = r.series.single()
        assertEquals(2, series.readings.size) // the reading without a valid timestamp is skipped
        val first = series.readings[0]
        assertEquals(BigDecimal("0.125"), first.value)
        assertTrue(first.isValid)
        assertEquals(java.time.OffsetDateTime.parse("2024-01-01T00:15:00+01:00").toInstant().toEpochMilli(), first.timestampMillis)
        assertFalse(series.readings[1].isValid)
    }

    @Test
    fun readingTypes_keepsOnlyEntriesWithCode() {
        val json = """[{"readingType": "A", "naziv": "N", "vrsta": "KOLICINA"}, {"naziv": "brez kode"},
            {"readingType": "B", "vrsta": "STANJE"}, {"readingType": "C", "vrsta": "DRUGO"}]"""
        val types = MojElektroParser.readingTypes(json)
        assertEquals(listOf("A", "B", "C"), types.map { it.code })
        assertEquals(listOf(ReadingKind.KOLICINA, ReadingKind.STANJE, ReadingKind.UNKNOWN), types.map { it.kind })
    }

    @Test
    fun errorCode_acceptsSingleAndListForm() {
        assertEquals("E.1.001", MojElektroParser.errorCode("""{"koda": "E.1.001", "opis": "x"}"""))
        assertEquals("E.2.123", MojElektroParser.errorCode("""{"errors": [{"koda": "E.2.123"}]}"""))
        assertNull(MojElektroParser.errorCode("""{"koda": "<script>"}"""))
        assertNull(MojElektroParser.errorCode("not json"))
        assertNull(MojElektroParser.errorCode(null))
    }

    @Test
    fun mapResponse_mapsDocumentedStatuses() {
        fun map(status: Int, body: String? = null) =
            mapResponse(MojElektroClient.Response(status, body), MojElektroParser::meteringPoint)

        assertTrue(map(200, meteringPointJson) is MojElektroResult.Success)
        assertEquals(MojElektroResult.Failure(MojElektroError.InvalidResponse), map(200, "{}"))
        assertEquals(MojElektroResult.Failure(MojElektroError.InvalidResponse), map(200, null))
        assertEquals(MojElektroResult.Failure(MojElektroError.InvalidRequest("E.1.002")), map(400, """{"koda":"E.1.002"}"""))
        assertEquals(MojElektroResult.Failure(MojElektroError.Unauthorized), map(401))
        assertEquals(MojElektroResult.Failure(MojElektroError.Forbidden), map(403))
        assertEquals(MojElektroResult.Failure(MojElektroError.NotFound), map(404))
        assertEquals(MojElektroResult.Failure(MojElektroError.Server(503)), map(503))
        assertEquals(MojElektroResult.Failure(MojElektroError.Network), map(MojElektroClient.STATUS_NETWORK))
    }

    @Test
    fun meterReadingsUrl_repeatsOptionPerRegister() {
        val query = MojElektroClient.meterReadingsQuery(
            "3-1234567", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2), listOf("1.2", "3.4")
        )
        val url = MojElektroClient.buildUrl("/meter-readings", query)
        assertEquals(
            "https://api.informatika.si/mojelektro/v1/meter-readings?usagePoint=3-1234567" +
                "&startTime=2024-01-01&endTime=2024-01-02&option=ReadingType%3D1.2&option=ReadingType%3D3.4",
            url
        )
    }

    @Test
    fun input_eimmFollowsApiFormat() {
        assertEquals("3-1234567", MojElektroInput.normalizeEimm("  3-1234567 "))
        assertEquals("12-1", MojElektroInput.normalizeEimm("12-1"))
        assertNull(MojElektroInput.normalizeEimm("123-1"))
        assertNull(MojElektroInput.normalizeEimm("3_1234567"))
        assertNull(MojElektroInput.normalizeEimm("3-"))
        assertNull(MojElektroInput.normalizeEimm(""))
    }

    @Test
    fun input_tokenRejectsCopyMistakesAndHeaderInjection() {
        assertEquals("abc123", MojElektroInput.normalizeToken("  abc123\n"))
        assertNull(MojElektroInput.normalizeToken(""))
        assertNull(MojElektroInput.normalizeToken("abc 123"))
        assertNull(MojElektroInput.normalizeToken("abc\r\nX-Other: 1"))
        assertNull(MojElektroInput.normalizeToken("a".repeat(3000)))
    }

    @Test
    fun credentials_neverPrintToken() {
        assertFalse(MojElektroCredentials("secret-token", "3-1").toString().contains("secret-token"))
    }

    @Test
    fun messages_areSlovenianAndSpecific() {
        assertTrue(mojElektroErrorMessage(MojElektroError.Forbidden).contains("EIMM"))
        assertTrue(mojElektroErrorMessage(MojElektroError.InvalidRequest("E.1.001")).endsWith("(koda E.1.001)"))
        assertTrue(mojElektroErrorMessage(MojElektroError.Server(500)).contains("500"))
        assertEquals(
            "Povezava deluje. Merilno mesto 3-1234567, merilne točke: 1. Merilno mesto ima 15-minutne meritve.",
            successText(MojElektroParser.meteringPoint(meteringPointJson))
        )
    }
}
