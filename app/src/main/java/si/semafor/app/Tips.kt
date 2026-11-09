package si.semafor.app

/**
 * Vsebina zaslona "Nasveti". Vsak element je par (naslov, besedilo).
 * Za urejanje nasvetov spremeni samo ta seznam.
 */
object Tips {
    const val TITLE = "Nasveti za prilagajanje porabe"

    const val INTRO =
        "Omrežnina se obračunava po časovnih blokih. Najdražji je blok 1, najcenejši blok 5. " +
                "Največ lahko prihraniš, če velike porabnike prestaviš v cenejše bloke in se izogneš " +
                "hkratnemu vklopu več močnih naprav."

    const val NOTE = "Nasveti so splošni in informativni."

    const val MORE_LABEL = "Več nasvetov na uro.si"
    const val MORE_URL = "https://www.uro.si/forum/nasveti-u%C4%8Dinkovita-raba-omre%C5%BEij"

    val ALL: List<Pair<String, String>> = listOf(
        "Prestavi močne naprave v cenejše bloke" to
                "Pralni in pomivalni stroj, sušilnik ter polnjenje električnega vozila po možnosti zaženi ponoči ali v blokih z nižjo ceno. Trenutni blok in naslednji najugodnejši blok vidiš na domačem zaslonu.",
        "Ne vklopi vsega hkrati" to
                "Postavka za moč se obračunava glede na obračunsko moč v posameznem bloku. Konice moči v posameznem bloku vplivajo na to, kakšno obračunsko moč ti operater določi za naslednje leto.",
        "Izogibaj se preseganju dogovorjene moči" to
                "Če dogovorjeno obračunsko moč preseževaš, se v omrežnini upošteva tudi presežna moč.",
        "Uporabi zakasnjen zagon" to
                "Večina pralnih in pomivalnih strojev ima časovnik. Nastavi ga tako, da se program začne v cenejšem bloku.",
        "Polnjenje električnega vozila" to
                "Če imaš možnost, nastavi urnik ali omejitev moči polnjenja, da v dražjih blokih ne ustvariš visoke konice.",
        "Preveri svojo dogovorjeno obračunsko moč" to
                "Vrednosti za posamezni blok najdeš na računu za omrežnino.",
        "Višja sezona je dražja" to
                "V višji sezoni (november–februar) velja tudi blok 1, vendar samo ob delavnikih. Takrat je prilagajanje porabe najpomembnejše."
    )
}
