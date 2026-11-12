package si.semafor.app

/**
 * Konfiguracija za prostovoljne donacije prek Revoluta.
 * URL_1, URL_3, URL_5 in URL_CUSTOM vodijo do spletne strani Revolut.
 * URL_CUSTOM omogoča, da poljuben znesek vpiše plačnik neposredno v Revolutu.
 */
object DonationConfig {
    const val DONATIONS_ENABLED = true
    const val URL_1 = "https://revolut.me/primozh?currency=EUR&amount=100"
    const val URL_3 = "https://revolut.me/primozh?currency=EUR&amount=300"
    const val URL_5 = "https://revolut.me/primozh?currency=EUR&amount=500"
    const val URL_CUSTOM = "https://revolut.me/primozh"
}
