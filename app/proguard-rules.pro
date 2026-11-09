# Berljivi sledovi sklada v Play Console / Crashlytics (skupaj z mapping.txt)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WorkManager ustvari delavce z refleksijo (ime razreda + konstruktor)
-keep class si.semafor.app.UpdateWorker { <init>(...); }

# Manifestne komponente (widget, aktivnost) R8 zadrži samodejno prek manifest pravil.
# Aplikacija ne uporablja Gson/Moshi/Retrofit/Firebase; JSON in XML obdelava
# poteka prek org.json in XmlPullParser iz Android ogrodja.

# Release: v produkcijski različici ni razhroščevalnih izpisov (verbose/debug/info), tudi iz knjižnic.
# Aplikacija trenutno sama ne piše v dnevnik; pravilo je varovalka za prihodnje spremembe.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
