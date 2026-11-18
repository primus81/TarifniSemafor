package si.semafor.app

import android.app.Application

/**
 * Applies the chosen language before any Activity, widget update or notification is created, and loads the price data
 * (a small local file), so the widget and the notifications use the same network tariffs as the app.
 */
class SemaforApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLanguage.init(this)
        si.semafor.app.pricing.PriceRepository.init(this)
    }
}
