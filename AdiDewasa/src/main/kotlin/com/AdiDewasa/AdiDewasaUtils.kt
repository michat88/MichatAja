package com.AdiDewasa

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils
import com.fasterxml.jackson.annotation.JsonProperty
import java.net.URLEncoder

object AdiDewasaUtils {

    // Headers
    val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/plain, */*",
        "Referer" to "https://dramafull.cc/"
    )

    // Data Class untuk Hasil Lengkap TMDB
    data class TmdbDetails(
        val tmdbId: Int?,
        val imdbId: String?,
        val rating: Int? // Skala 0-100
    )

    // Model JSON Internal
    data class TmdbSearch(@JsonProperty("results") val results: List<TmdbRes>? = null)
    data class TmdbRes(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("vote_average") val vote_average: Double? = null // Ambil Rating
    )
    data class TmdbExternalIds(@JsonProperty("imdb_id") val imdb_id: String? = null)

    // --- FUNGSI PENCARI LENGKAP (ID + RATING) ---
    suspend fun getTmdbDetails(title: String, year: Int?, isMovie: Boolean): TmdbDetails {
        try {
            val apiKey = "b030404650f279792a8d3287232358e3"
            val type = if (isMovie) "movie" else "tv"
            val q = URLEncoder.encode(title, "UTF-8")
            
            // 1. Cari TMDB ID & Rating
            var tmdbItem: TmdbRes? = null
            
            // Prioritas: Cari dengan Tahun
            if (year != null) {
                val url = "https://api.themoviedb.org/3/search/$type?api_key=$apiKey&query=$q&year=$year"
                tmdbItem = app.get(url).parsedSafe<TmdbSearch>()?.results?.firstOrNull()
            }
            
            // Fallback: Cari tanpa Tahun
            if (tmdbItem == null) {
                val urlNoYear = "https://api.themoviedb.org/3/search/$type?api_key=$apiKey&query=$q"
                tmdbItem = app.get(urlNoYear).parsedSafe<TmdbSearch>()?.results?.firstOrNull()
            }

            if (tmdbItem?.id == null) return TmdbDetails(null, null, null)

            // Hitung Rating (TMDB 0-10 -> Cloudstream 0-100)
            val ratingInt = tmdbItem.vote_average?.times(10)?.toInt()

            // 2. Cari IMDB ID
            val extUrl = "https://api.themoviedb.org/3/$type/${tmdbItem.id}/external_ids?api_key=$apiKey"
            val imdbId = app.get(extUrl).parsedSafe<TmdbExternalIds>()?.imdb_id

            return TmdbDetails(tmdbItem.id, imdbId, ratingInt)

        } catch (e: Exception) {
            return TmdbDetails(null, null, null)
        }
    }

    // --- FUNGSI WYZIE ---
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
        } catch (e: Exception) { }
    }
}
