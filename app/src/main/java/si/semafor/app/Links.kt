package si.semafor.app

object Links {
    const val URO_URL = "https://www.uro.si"
    const val TARIFF_INFO = "https://mojelektro.si/vse_o_novem_tarifnem_sistemu"
    const val CALC_URL = "https://aplikacije.agen-rs.si/web/primerjalnikPonudb"
    const val CALC_TERMS = "https://www.agen-rs.si/primerjalnik/pogoji-uporabe"
    const val CALC_INFO = "https://www.agen-rs.si/primerjalnik"
    const val NEWS_FEED =
        "https://www.agen-rs.si/novice/-/journal/rss/46801?doAsGroupId=10926&refererPlid=10944&controlPanelCategory=current_site.pages&_15_groupId=10926&newsrss"
    const val NEWS_PAGE = "https://www.agen-rs.si/novice"

    // Portal of the distribution operators where the user creates the read-only API token (optional connection).
    const val MOJ_ELEKTRO = "https://mojelektro.si"

    // "O aplikaciji": the developer's site, the terms of use and the app's website.
    const val DEVELOPER = "https://primush.online/"
    const val TERMS = "https://tarifnisemafor.si/pogoji-uporabe/"
    const val WEBSITE = "https://tarifnisemafor.si/"

    // Icons of the app (Material Symbols, Apache License 2.0).
    const val MATERIAL_SYMBOLS = "https://fonts.google.com/icons"

    // Official description of the Moj Elektro API (the only server the optional connection talks to).
    const val MOJ_ELEKTRO_API_DOCS = "https://docs.informatika.si/mojelektro/api/"

    // Privacy policy and disclaimer of the app and the website (both pages: Slovenian first, English under #en).
    // Until 2.2.3 they were on primus81.github.io; those addresses now redirect here.
    // If PRIVACY_POLICY contains "REPLACE_WITH", the line in the "O aplikaciji" dialog is not shown.
    const val PRIVACY_POLICY = "https://tarifnisemafor.si/pravilnik-o-zasebnosti/"
    const val DISCLAIMER = "https://tarifnisemafor.si/izjava-o-omejitvi-odgovornosti/"

    /** The privacy policy in the app's language: the English part of the page for English. */
    fun privacyPolicy(language: String): String =
        if (language == AppSettings.LANGUAGE_EN) "$PRIVACY_POLICY#en" else PRIVACY_POLICY
}
