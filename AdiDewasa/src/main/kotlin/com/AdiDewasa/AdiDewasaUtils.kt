package com.AdiDewasa

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils
import java.net.URLEncoder

object AdiDewasaUtils {

    // --- KONFIGURASI HEADER AGAR TIDAK DIBLOKIR ---
    val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/plain, */*",
        "Referer" to "https://dramafull.cc/"
    )

    // --- FUNGSI MENCARI ID TMDB & IMDB (UNTUK HYBRID SYSTEM) ---
    suspend fun getTmdbAndImdbId(title: String, year: Int?, isMovie: Boolean): Pair<Int?, String?> {
        try {
            val apiKey = "b030404650f279792a8d3287232358e3" // Cloudstream Public Key
            val type = if (isMovie) "movie" else "tv"
            val q = URLEncoder.encode(title, "UTF-8")
            
            // 1. Cari TMDB ID
            var tmdbId: Int? = null
            
            // Prioritas: Cari dengan Tahun
            if (year != null) {
                val url = "https://api.themoviedb.org/3/search/$type?api_key=$apiKey&query=$q&year=$year"
                val res = app.get(url).parsedSafe<TmdbSearch>()?.results?.firstOrNull()?.id
                tmdbId = res
            }
            
            // Fallback: Cari tanpa Tahun
            if (tmdbId == null) {
                val urlNoYear = "https://api.themoviedb.org/3/search/$type?api_key=$apiKey&query=$q"
                tmdbId = app.get(urlNoYear).parsedSafe<TmdbSearch>()?.results?.firstOrNull()?.id
            }

            if (tmdbId == null) return Pair(null, null)

            // 2. Cari IMDB ID (External IDs)
            val extUrl = "https://api.themoviedb.org/3/$type/$tmdbId/external_ids?api_key=$apiKey"
            val imdbId = app.get(extUrl).parsedSafe<TmdbExternalIds>()?.imdb_id

            return Pair(tmdbId, imdbId)

        } catch (e: Exception) {
            return Pair(null, null)
        }
    }

    // --- FUNGSI MENGAMBIL SUBTITLE WYZIE ---
    suspend fun invokeWyzieSubtitle(tmdbId: Int, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit) {
        val wyzieUrl = if (season == null) 
            "https://sub.wyzie.ru/search?id=$tmdbId"
        else 
            "https://sub.wyzie.ru/search?id=$tmdbId&season=$season&episode=$episode"
        
        try {
            val res = app.get(wyzieUrl).text
            val jsonArr = org.json.JSONArray(res)
            for (i in 0 until jsonArr.length()) {
                val item = jsonArr.getJSONObject(i)
                subtitleCallback(
                    SubtitleFile(item.getString("display"), item.getString("url"))
                )
            }
        } catch (e: Exception) { 
            // Ignore error
        }
    }
}
