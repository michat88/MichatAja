package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.metaproviders.TmdbProvider // Wajib Import Ini

class AdiDewasa : TmdbProvider() { // Mewarisi TmdbProvider, bukan MainAPI biasa
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Gunakan API Key Publik Cloudstream
    private val apiKey = "b030404650f279792a8d3287232358e3"
    private val tmdbApi = "https://api.themoviedb.org/3"

    // --- KONFIGURASI HALAMAN UTAMA (TMDb MODE) ---
    // Kita filter agar memunculkan konten dewasa (include_adult=true)
    override val mainPage = mainPageOf(
        "$tmdbApi/discover/movie?api_key=$apiKey&include_adult=true&sort_by=popularity.desc" to "Adult Movies - Populer",
        "$tmdbApi/discover/movie?api_key=$apiKey&include_adult=true&sort_by=primary_release_date.desc" to "Adult Movies - Terbaru",
        "$tmdbApi/discover/movie?api_key=$apiKey&include_adult=true&sort_by=vote_average.desc&vote_count.gte=50" to "Adult Movies - Rating Tinggi",
        "$tmdbApi/discover/tv?api_key=$apiKey&include_adult=true&sort_by=popularity.desc" to "Adult TV - Populer",
        "$tmdbApi/discover/tv?api_key=$apiKey&include_adult=true&sort_by=first_air_date.desc" to "Adult TV - Terbaru"
    )

    // --- LOAD LINKS (MENGGUNAKAN ADIHYBRID) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        // Karena ini TmdbProvider, data yang masuk adalah LinkData (format TMDB)
        val res = AppUtils.parseJson<LinkData>(data)

        // Kita jalankan pencarian video secara paralel
        AppUtils.runAllAsync(
            {
                // 1. Wyzie (Subtitle)
                if (res.tmdbId != null) {
                    AdiDewasaUtils.invokeWyzieSubtitle(res.tmdbId, res.season, res.episode, subtitleCallback)
                }
            },
            {
                // 2. Adimoviebox (Berdasarkan Judul)
                // Kita gunakan judul asli (title) atau judul original (orgTitle)
                val searchTitle = res.title ?: res.orgTitle ?: ""
                if (searchTitle.isNotEmpty()) {
                    AdiHybrid.invokeAdimoviebox(searchTitle, res.year, res.season, res.episode, subtitleCallback, callback)
                }
            },
            {
                // 3. Idlix (Berdasarkan Judul)
                val searchTitle = res.title ?: res.orgTitle ?: ""
                if (searchTitle.isNotEmpty()) {
                    AdiHybrid.invokeIdlix(searchTitle, res.year, res.season, res.episode, subtitleCallback, callback)
                }
            },
            {
                // 4. Vidlink (Berdasarkan TMDB ID)
                if (res.tmdbId != null) {
                    AdiHybrid.invokeVidlink(res.tmdbId, res.season, res.episode, callback)
                }
            },
            {
                // 5. Superembed (Berdasarkan TMDB ID)
                if (res.tmdbId != null) {
                    AdiHybrid.invokeSuperembed(res.tmdbId, res.season, res.episode, subtitleCallback, callback)
                }
            }
        )

        return true
    }
}
