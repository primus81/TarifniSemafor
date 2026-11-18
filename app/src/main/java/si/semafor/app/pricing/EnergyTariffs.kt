package si.semafor.app.pricing

import si.semafor.app.SloTime
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month

/** How the metering point measures energy: one tariff all day, or a higher (VT) and a lower (MT) daily tariff. */
enum class MeterType { SINGLE, DUAL }

/** Daily tariff of the energy price: enotna (ET), visoka (VT), mala (MT). */
enum class EnergyTariff { ET, VT, MT }

/**
 * Work-free days in Slovenia (dela prosti dnevi) under the Zakon o praznikih in dela prostih dnevih v Republiki
 * Sloveniji (ZPDPD), as listed by the government (https://www.gov.si/teme/drzavni-prazniki-in-dela-prosti-dnevi/);
 * checked against the amendment ZPDPD-I (Uradni list RS 1567/2026), which added no work-free day. Holidays that are
 * not work-free (for example 8 June, 7 August) are deliberately not here.
 */
object SloHolidays {
    const val SOURCE_URL = "https://www.gov.si/teme/drzavni-prazniki-in-dela-prosti-dnevi/"

    /** Easter Sunday of the Gregorian calendar (anonymous Gregorian algorithm). */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    fun workFreeDays(year: Int): Set<LocalDate> {
        val easter = easterSunday(year)
        return setOf(
            LocalDate.of(year, Month.JANUARY, 1),
            LocalDate.of(year, Month.JANUARY, 2),
            LocalDate.of(year, Month.FEBRUARY, 8),
            easter,
            easter.plusDays(1), // Easter Monday
            LocalDate.of(year, Month.APRIL, 27),
            LocalDate.of(year, Month.MAY, 1),
            LocalDate.of(year, Month.MAY, 2),
            easter.plusDays(49), // Whit Sunday (binkošti)
            LocalDate.of(year, Month.JUNE, 25),
            LocalDate.of(year, Month.AUGUST, 15),
            LocalDate.of(year, Month.OCTOBER, 31),
            LocalDate.of(year, Month.NOVEMBER, 1),
            LocalDate.of(year, Month.DECEMBER, 25),
            LocalDate.of(year, Month.DECEMBER, 26)
        )
    }

    private val cache = HashMap<Int, Set<LocalDate>>()

    fun isWorkFree(date: LocalDate): Boolean = synchronized(cache) {
        cache.getOrPut(date.year) { workFreeDays(date.year) }
    }.contains(date)
}

/** Which daily tariff of the energy price applies at a moment. Pure Kotlin, unit tested. */
object EnergyTariffs {
    fun isWeekendOrHoliday(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY || SloHolidays.isWorkFree(date)

    /** The tariff at [millis] (Slovenian wall clock). ET for single-tariff metering. */
    fun tariffAt(millis: Long, meter: MeterType, times: TariffTimes): EnergyTariff {
        if (meter == MeterType.SINGLE) return EnergyTariff.ET
        val local = Instant.ofEpochMilli(millis).atZone(SloTime.ZONE)
        return tariffAt(local.toLocalDate(), local.hour, times)
    }

    /** The two-tariff rule for one hour of a local date. */
    fun tariffAt(date: LocalDate, hour: Int, times: TariffTimes): EnergyTariff {
        if (times.mtOnWeekendsAndHolidays && isWeekendOrHoliday(date)) return EnergyTariff.MT
        return if (SloTime.coversHour(hour, times.vtStartHour, times.vtEndHour)) EnergyTariff.VT else EnergyTariff.MT
    }
}
