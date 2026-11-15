package si.semafor.app

import android.app.Application

/** Applies the chosen language before any Activity, widget update or notification is created. */
class SemaforApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLanguage.init(this)
    }
}
