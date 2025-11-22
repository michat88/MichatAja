package com.MamaPapaPunya

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class MamaPapaPunyaPlugin : Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan Provider Utama
        registerMainAPI(MamaPapaPunya())
        // Mendaftarkan Extractor Helper (Jeniusplay)
        registerExtractorAPI(Jeniusplay2())
    }
}
