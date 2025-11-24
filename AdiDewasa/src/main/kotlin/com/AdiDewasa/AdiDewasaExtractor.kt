package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.SubtitleFile
import org.json.JSONObject

object AdiDewasaExtractor {

    suspend fun invokeAll(
        info: AdiLinkInfo,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val targetUrl = info.url

        listOf(
            // TUGAS 1: DRAMAFULL
            suspend { invokeOriginalDramafull(targetUrl, subtitleCallback, callback) },

            // TUGAS 2: HYBRID EXTRACTORS
            suspend { 
                if (info.title.isNotEmpty()) {
                    val isMovie = info.season == null

                    // A. Extractor Judul (Jalan Langsung)
                    AdiHybrid.invokeAdimoviebox(info.title, info.year, info.season, info.episode, subtitleCallback, callback)
                    AdiHybrid.invokeIdlix(info.title, info.year, info.season, info.episode, subtitleCallback, callback)

                    // B. Extractor ID (Cari TMDB Dulu)
                    val details = AdiDewasaUtils.getTmdbDetails(info.title, info.year, isMovie)
                    val tmdbId = details.tmdbId
                    // val imdbId = details.imdbId // Jika butuh imdb, pakai ini

                    if (tmdbId != null) {
                        AdiDewasaUtils.invokeWyzieSubtitle(tmdbId, info.season, info.episode, subtitleCallback)
                        AdiHybrid.invokeVidlink(tmdbId, info.season, info.episode, callback)
                        AdiHybrid.invokeSuperembed(tmdbId, info.season, info.episode, subtitleCallback, callback)
                    }
                }
            }
        ).amap { it.invoke() }
    }

    private suspend fun invokeOriginalDramafull(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val doc = app.get(url, headers = AdiDewasaUtils.headers).document
            val script = doc.select("script:containsData(signedUrl)").firstOrNull()?.toString() ?: return
            
            val signedUrl = Regex("""window\.signedUrl\s*=\s*["'](.+?)["']""").find(script)?.groupValues?.get(1)?.replace("\\/", "/") 
                ?: return
            
            val res = app.get(signedUrl, headers = AdiDewasaUtils.headers).text
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
                        this.referer = url
                        this.headers = AdiDewasaUtils.headers
                    }
                )
                
                val subJson = resJson.optJSONObject("sub")
                subJson?.optJSONArray(bestQualityKey)?.let { array ->
                    for (i in 0 until array.length()) {
                        val subUrl = array.getString(i)
                        subtitleCallback(SubtitleFile("English (Original)", "https://dramafull.cc$subUrl"))
                    }
                }
            }
        } catch (e: Exception) { }
    }
}
