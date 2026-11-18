package si.semafor.app.planner

import si.semafor.app.mojelektro.PowerSummary
import si.semafor.app.pricing.KwhPrice

/** One short recommendation of the "Kaj lahko naredim zdaj?" card. */
sealed interface NowAdvice {
    /** True = calculated from the user's own prices or data; false = a general hint (shown as such). */
    val personal: Boolean

    /** The final price now is (within 0.2 cent) the lowest of the coming hours. */
    data class GoodNow(val priceNow: Double, val hours: Int) : NowAdvice {
        override val personal = true
    }

    /** A cheaper hour comes later. */
    data class WaitForCheaper(val atMillis: Long, val block: Int, val priceNow: Double, val priceThen: Double) : NowAdvice {
        override val personal = true
    }

    /** A planned appliance should start soon or now ([minutes] 0 = now). */
    data class DeviceSoon(val name: String, val startMillis: Long, val minutes: Long) : NowAdvice {
        override val personal = true
    }

    /** The next planned appliance later today or tomorrow. */
    data class DeviceNext(val name: String, val startMillis: Long) : NowAdvice {
        override val personal = true
    }

    /** The energy price is the same all day: differences come from the network charge only. */
    data object FlatEnergy : NowAdvice {
        override val personal = true
    }

    /** The latest (delayed) readings show the agreed power exceeded (share > 1) or nearly reached (>= 0.9). */
    data class PowerHigh(val summary: PowerSummary, val share: Double) : NowAdvice {
        override val personal = true
    }

    /** No own prices: only the block information of uro.si ([bestBlock] / [bestAtMillis] may be null). */
    data class General(val currentBlock: Int?, val bestBlock: Int?, val bestAtMillis: Long?) : NowAdvice {
        override val personal = false
    }
}

/** Builds the recommendations, the most important first. Pure Kotlin, unit tested. */
object NowAdvisor {
    /** Two prices closer than this (EUR/kWh with VAT) count as equal. */
    const val EQUAL_PRICE = 0.002

    /** Hours looked ahead for "good now" / "wait". */
    const val LOOK_AHEAD_HOURS = 12

    fun advise(
        nowMillis: Long,
        prices: List<KwhPrice>,
        flatEnergy: Boolean,
        nextRun: PlannerStore.PlannedRun?,
        power: PowerSummary?,
        currentBlock: Int?,
        bestBlock: Int?,
        bestAtMillis: Long?
    ): List<NowAdvice> {
        val out = ArrayList<NowAdvice>()
        if (nextRun != null && nextRun.endMillis > nowMillis) {
            val minutes = ((nextRun.startMillis - nowMillis) / 60_000L).coerceAtLeast(0)
            out += if (minutes <= 60) NowAdvice.DeviceSoon(nextRun.name, nextRun.startMillis, minutes)
            else NowAdvice.DeviceNext(nextRun.name, nextRun.startMillis)
        }
        val window = prices.take(LOOK_AHEAD_HOURS)
        if (window.size >= 2) {
            val now = window.first()
            val min = window.minOf { it.total }
            if (now.total <= min + EQUAL_PRICE) {
                out += NowAdvice.GoodNow(now.total, window.size)
            } else {
                val then = window.drop(1).first { it.total <= min + EQUAL_PRICE }
                out += NowAdvice.WaitForCheaper(then.startMillis, then.block, now.total, then.total)
            }
            if (flatEnergy) out += NowAdvice.FlatEnergy
        } else {
            out += NowAdvice.General(currentBlock, bestBlock, bestAtMillis?.takeIf { it > nowMillis })
        }
        val share = power?.peakShare
        if (power != null && share != null && share >= 0.9) out += NowAdvice.PowerHigh(power, share)
        // Order: what to do now first, then the appliance, then background information.
        return out.sortedBy {
            when (it) {
                is NowAdvice.DeviceSoon -> 0
                is NowAdvice.GoodNow, is NowAdvice.WaitForCheaper -> 1
                is NowAdvice.General -> 1
                is NowAdvice.DeviceNext -> 2
                is NowAdvice.PowerHigh -> 3
                NowAdvice.FlatEnergy -> 4
            }
        }
    }
}
