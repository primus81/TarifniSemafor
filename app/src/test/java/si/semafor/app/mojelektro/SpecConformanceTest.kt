package si.semafor.app.mojelektro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Answers shaped exactly like the schemas of the official OpenAPI description
 * (https://docs.informatika.si/mojelektro/api/mojelektro-openapi.yaml, version 1.0). The values are made up.
 */
class SpecConformanceTest {
    @Test
    fun dateTimeExampleOfTheSpecIsParsed() {
        // The spec's own DateTime example carries seconds.
        val millis = MojElektroParser.instantMillis("2023-12-19T00:00:15+02:00")
        assertEquals(java.time.OffsetDateTime.parse("2023-12-18T22:00:15Z").toInstant().toEpochMilli(), millis)
    }

    @Test
    fun readingQualitiesCatalogue() {
        val map = MojElektroParser.readingQualities(
            """[{"readingQualityType": "1.4.9", "description": "Ocenjena vrednost"},
                {"readingQualityType": "1.4.16"},
                {"description": "brez kode"}]"""
        )
        assertEquals(mapOf("1.4.9" to "Ocenjena vrednost", "1.4.16" to "1.4.16"), map)
        assertTrue(MojElektroParser.readingQualities("[]").isEmpty())
    }

    @Test
    fun readingWithoutQualitiesIsValid() {
        // "V kolikor je seznam prazen ali ni prisoten, se odčitek smatra kot validen."
        val r = MojElektroParser.meterReadings(
            """{"usagePoint": "3-1234567", "intervalBlocks": [{"readingType": "32.0.2.4.1.2.12.0.0.0.0.0.0.0.0.3.72.0",
               "intervalReadings": [
                 {"timestamp": "2023-12-19T00:15:00+01:00", "value": "0.125"},
                 {"timestamp": "2023-12-19T00:30:00+01:00", "value": "0.250", "readingQualities": []},
                 {"timestamp": "2023-12-19T00:45:00+01:00", "value": "0.300",
                  "readingQualities": [{"readingQualityType": "1.4.9"}]}]}]}"""
        )
        val readings = r.series.single().readings
        assertEquals(listOf(true, true, false), readings.map { it.isValid })
    }

    @Test
    fun meterReadingsQueryFollowsTheSpec() {
        // startTime / endTime as LLLL-MM-DD, every register as option=ReadingType=<code>.
        val q = MojElektroClient.meterReadingsQuery(
            "3-1234567",
            java.time.LocalDate.of(2023, 12, 19),
            java.time.LocalDate.of(2023, 12, 20),
            listOf("32.0.2.4.1.2.12.0.0.0.0.0.0.0.0.3.72.0")
        )
        assertEquals(
            listOf(
                "usagePoint" to "3-1234567",
                "startTime" to "2023-12-19",
                "endTime" to "2023-12-20",
                "option" to "ReadingType=32.0.2.4.1.2.12.0.0.0.0.0.0.0.0.3.72.0"
            ),
            q
        )
        assertTrue(MojElektroClient.buildUrl("/meter-readings", q).startsWith("https://api.informatika.si/mojelektro/v1/meter-readings?"))
    }
}
