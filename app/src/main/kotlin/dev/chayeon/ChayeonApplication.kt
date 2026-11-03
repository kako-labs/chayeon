package dev.chayeon

import android.app.Application

class ChayeonApplication : Application() {
    val graph: AppGraph by lazy { AppGraph.foundation(serverUrl = BuildConfig.SERVER_URL) }
}
