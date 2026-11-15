package si.semafor.app

/**
 * Content of the "Nasveti" screen: pairs of string resources (title, text) in values/strings_features.xml (Slovenian)
 * and values-en/ (English). To edit a tip, change the resources; to add one, add a pair here and in both files.
 */
object Tips {
    const val MORE_URL = "https://www.uro.si/forum/nasveti-u%C4%8Dinkovita-raba-omre%C5%BEij"

    val ALL: List<Pair<Int, Int>> = listOf(
        R.string.tip1_title to R.string.tip1_text,
        R.string.tip2_title to R.string.tip2_text,
        R.string.tip3_title to R.string.tip3_text,
        R.string.tip4_title to R.string.tip4_text,
        R.string.tip5_title to R.string.tip5_text,
        R.string.tip6_title to R.string.tip6_text,
        R.string.tip7_title to R.string.tip7_text
    )
}
