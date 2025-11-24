package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.SubtitleFile
import com.AdiDrakor.AdiDrakorExtractor // Import Kekuatan AdiDrakor
import org.json.JSONObject
import java.net.URLEncoder

// Data Class Shared (Dipakai di AdiDewasa.kt juga)
data class AdiLinkInfo(
    val url: String,
    val title: String,
    val year: Int?,
    val episode: Int? = null,
    val season: Int? = null
)

object AdiDewasaExtractor {

    // --- FUNGSI UTAMA YANG DIPANGGIL DARI ADIDEWASA.KT ---
    suspend fun invokeAll(
        info: AdiLinkInfo,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val targetUrl = info.url

        // Jalankan Paralel (Original + Hybrid)
        AppUtils.runAllAsync(
            // TUGAS A: LOAD DARI DRAMAFULL (SUMBER UTAMA)
            {
                invokeOriginalDramafull(targetUrl, subtitleCallback, callback)
            },

            // TUGAS B: LOAD DARI ADIDRAKOR EXTRACTORS (SUMBER CADANGAN)
            {
                if (info.title.isNotEmpty()) {
                    val isMovie = info.season == null
                    // 1. Cari ID
                    val (tmdbId, imdbId) = getTmdbAndImdbId(info.title, info.year, isMovie)

                    if (tmdbId != null) {
                        // 2. Load Subtitle Wyzie
                        invokeWyzieSubtitle(tmdbId, info.season, info.episode, subtitleCallback)

                        // 3. Panggil Pasukan AdiDrakor
                        // a. Adimoviebox
                        AdiDrakorExtractor.invokeAdimoviebox(info.title, info.year, info.season, info.episode, subtitleCallback, callback)
                        
                        // b. Idlix
                        AdiDrakorExtractor.invokeIdlix(info.title, info.year, info.season, info.episode, subtitleCallback, callback)
                        
                        // c. Vidlink & Vidfast
                        AdiDrakorExtractor.invokeVidlink(tmdbId, info.season, info.episode, callback)
                        AdiDrakorExtractor.invokeVidfast(tmdbId, info.season, info.episode, subtitleCallback, callback)
                        
                        // d. Superembed
                        AdiDrakorExtractor.invokeSuperembed(tmdbId, info.season, info.episode, subtitleCallback, callback)

                        // e. Vidsrc (Perlu IMDB)
                        if (imdbId != null) {
                            AdiDrakorExtractor.invokeVidsrc(imdbId, info.season, info.episode, subtitleCallback, callback)
                            AdiDrakorExtractor.invokeVidsrccc(tmdbId, imdbId, info.season, info.episode, subtitleCallback, callback)
                            AdiDrakorExtractor.invokeWatchsomuch(imdbId, info.season, info.episode, subtitleCallback)
                        }

                        // f. Lainnya
                        AdiDrakorExtractor.invokeXprime(tmdbId, info.title, info.year, info.season, info.episode, subtitleCallback, callback)
                        AdiDrakorExtractor.invokeMapple(tmdbId, info.season, info.episode, subtitleCallback, callback)
                    }
                }
            }
        )
    }

    // --- LOGIKA ASLI DRAMAFULL (Dengan Fix Referer) ---
    private suspend fun invokeOriginalDramafull(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val doc = app.get(url).document
            val script = doc.select("script:containsData(signedUrl)").firstOrNull()?.toString() ?: return
            
            val signedUrl = Regex("""window\.signedUrl\s*=\s*["'](.+?)["']""").find(script)?.groupValues?.get(1)?.replace("\\/", "/") 
                ?: return
            
            val res = app.get(signedUrl).text
            val resJson = JSONObject(res)
            val videoSource = resJson.optJSONObject("video_source") ?: return
            
            val qualities = videoSource.keys().asSequence().toList()
                .sortedByDescending { it.toIntOrNull() ?: 0 }
            val bestQualityKey = qualities.firstOrNull() ?: return
            val bestQualityUrl = videoSource.optString(bestQualityKey)

            if (bestQualityUrl.isNotEmpty()) {
                callback(
                    newExtractorLink(
                        "AdiDewasa",
                        "AdiDewasa",
                        bestQualityUrl,
                        INFER_TYPE
                    ) {
                        this.referer = url // PENTING: Fix Error 3002
                        this.headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
                        )
                    }
                )
                
                // Subtitle Bawaan
                val subJson = resJson.optJSONObject("sub")
                subJson?.optJSONArray(bestQualityKey)?.let { array ->
                    for (i in 0 until array.length()) {
                        val subUrl = array.getString(i)
                        subtitleCallback(SubtitleFile("English (Original)", "https://dramafull.cc$subUrl"))
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore if Dramafull source fails
        }
    }

    // --- LOGIKA WYZIE ---
    private suspend fun invokeWyzieSubtitle(tmdbId: Int, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit) {
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

    // --- HELPER TMDB/IMDB SEARCH ---
    private data class TmdbSearch(val results: List<TmdbRes>?)
    private data class TmdbRes(val id: Int?)
    private data class TmdbExternalIds(val imdb_id: String?)

    private suspend fun getTmdbAndImdbId(title: String, year: Int?, isMovie: Boolean): Pair<Int?, String?> {
        try {
            val apiKey = "b030404650f279792a8d3287232358e3"
            val type = if (isMovie) "movie" else "tv"
            val q = URLEncoder.encode(title, "UTF-8")
            
            // 1. Cari TMDB ID
            var tmdbId: Int? = null
            
            if (year != null) {
                val url = "https://api.themoviedb.org/3/search/$type?api_key=$apiKey&query=$q&year=$year"
                val res = app.get(url).parsedSafe<TmdbSearch>()?.results?.firstOrNull()?.id
                tmdbId = res
            }
            
            if (tmdbId == null) {
                val urlNoYear = "https://api.themoviedb.org/3/search/$type?api_key=$apiKey&query=$q"
                tmdbId = app.get(urlNoYear).parsedSafe<TmdbSearch>()?.results?.firstOrNull()?.id
            }

            if (tmdbId == null) return Pair(null, null)

            // 2. Cari IMDB ID
            val extUrl = "https://api.themoviedb.org/3/$type/$tmdbId/external_ids?api_key=$apiKey"
            val imdbId = app.get(extUrl).parsedSafe<TmdbExternalIds>()?.imdb_id

            return Pair(tmdbId, imdbId)

        } catch (e: Exception) {
            return Pair(null, null)
        }
    }
}
