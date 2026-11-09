package si.semafor.app

/**
 * Stanje ikone v zgornji vrstici. Čista Kotlin logika brez Android importov (preverljiva z enotnimi testi).
 *
 * Logotip je ena sama strela brez ločljivih delov, zato se obarva cela: svetli (lit) z barvo trenutnega bloka
 * (barvo da klicatelj z Repo.color, da se ujema s karticami), sicer je ugasnjena.
 * Bloki 1-5 so veljavni; vse drugo (null, 0, 6, negativno) pomeni "ni podatkov".
 */
object SemaforIconState {
    data class State(val lit: Boolean, val blockId: Int?)

    private val OFF = State(lit = false, blockId = null)

    fun forBlock(blockId: Int?): State =
        if (blockId != null && blockId in 1..5) State(lit = true, blockId = blockId) else OFF
}
