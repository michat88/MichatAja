package com.AdiDewas

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.newSubtitleFile // Import yang hilang ditambahkan
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.json.JSONObject
import java.net.URLEncoder

object AdiDewasaExtractor {

    private const val baseUrl = "https://dramafull.cc"

    suspend fun invokeAdiDewasa(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // FIX: Menggunakan AdiDewasaHelper.normalizeQuery
        val cleanQuery = AdiDewasaHelper.normalizeQuery(title)
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8").replace("+", "%20")
        val searchUrl = "$baseUrl/api/live-search/$encodedQuery"

        try {
            val searchRes = app.get(searchUrl, headers = AdiDewasaHelper.headers)
                .parsedSafe<ApiSearchResponse>()
            
            val matchedItem = searchRes?.data?.find { item ->
                val itemTitle = item.title ?: item.name ?: ""
                // FIX: Menggunakan AdiDewasaHelper.isFuzzyMatch
                AdiDewasaHelper.isFuzzyMatch(title, itemTitle)
            } ?: searchRes?.data?.firstOrNull()

            if (matchedItem == null) return 

            val slug = matchedItem.slug ?: return
            var targetUrl = "$baseUrl/film/$slug"

            if (season != null && episode != null) {
                val doc = app.get(targetUrl, headers = AdiDewasaHelper.headers).document
                val episodeHref = doc.select("div.episode-item a, .episode-list a, .episodes a").find { 
                    val text = it.text().trim()
                    val epNum = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    epNum == episode
                }?.attr("href")

                if (episodeHref == null) return 
                targetUrl = if (episodeHref.startsWith("http")) episodeHref else "$baseUrl$episodeHref"
            } else {
                val doc = app.get(targetUrl, headers = AdiDewasaHelper.headers).document
                val watchBtn = doc.selectFirst("a.btn-watch, a.watch-now, .watch-button a")
                val href = watchBtn?.attr("href")
                if (!href.isNullOrBlank() && !href.contains("javascript")) {
                    targetUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                }
            }

            val pageRes = app.get(targetUrl, headers = AdiDewasaHelper.headers)
            val pageBody = pageRes.text
            
            val signedUrlRegex = Regex("""signedUrl\s*=\s*["']([^"']+)["']""")
            val match = signedUrlRegex.find(pageBody)
            val rawSignedUrl = match?.groupValues?.get(1) ?: return
            val signedUrl = rawSignedUrl.replace("\\/", "/")
            
            val jsonRes = app.get(signedUrl, headers = AdiDewasaHelper.headers, referer = targetUrl).text
            val jsonObject = tryParseJson<JSONObject>(jsonRes) ?: return
            val videoSource = jsonObject.optJSONObject("video_source") ?: return
            
            val qualities = videoSource.keys().asSequence().toList()
                .sortedByDescending { it.toIntOrNull() ?: 0 }
            val bestQualityKey = qualities.firstOrNull()

            videoSource.keys().forEach { quality ->
                val link = videoSource.optString(quality)
                if (link.isNotEmpty()) {
                    callback.invoke(
                        newExtractorLink(
                            "AdiDewasa",
                            "AdiDewasa ($quality)",
                            link,
                            INFER_TYPE
                        )
                    )
                }
            }

            if (bestQualityKey != null) {
                val subJson = jsonObject.optJSONObject("sub")
                val subArray = subJson?.optJSONArray(bestQualityKey)
                
                if (subArray != null) {
                    for (i in 0 until subArray.length()) {
                        val subPath = subArray.getString(i)
                        val subUrl = if (subPath.startsWith("http")) subPath else "$baseUrl$subPath"
                        
                        subtitleCallback.invoke(
                            newSubtitleFile("English (Internal)", subUrl)
                        )
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
