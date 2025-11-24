package com.AdiDewasa

import com.lagradost.cloudstream3.subtitles.AbstractSubtitleProvider
import com.lagradost.cloudstream3.subtitles.SubtitleSearchResponse
import com.lagradost.cloudstream3.subtitles.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.Jsoup

class MySubSource : AbstractSubtitleProvider() {
    override val name = "MySubSource (Indo)"
    override val idPrefix = "mysubsource"

    // Kita fokuskan pencarian hanya untuk Bahasa Indonesia agar lebih cepat
    override suspend fun search(query: SubtitleSearchRequest): List<SubtitleSearchResponse> {
        val cleanQuery = query.query.replace(" ", "+")
        val url = "https://subsource.net/search/$cleanQuery"
        
        try {
            val doc = app.get(url).document
            val results = doc.select("div.movie-list div.movie-entry")
            
            return results.mapNotNull {
                val title = it.select("div.movie-name").text()
                val href = it.select("a").attr("href")
                // Ambil link detailnya
                val fullUrl = if (href.startsWith("http")) href else "https://subsource.net$href"
                
                // Pastikan yang kita ambil sesuai dengan film yang dicari
                SubtitleSearchResponse(
                    title = title,
                    url = fullUrl,
                    apiName = name
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    override suspend fun load(data: SubtitleSearchResponse): List<SubtitleFile> {
        try {
            val doc = app.get(data.url).document
            val subList = mutableListOf<SubtitleFile>()
            
            // Cari bagian Bahasa Indonesia
            // Struktur SubSource: Ada tab atau div untuk setiap bahasa
            val indoElements = doc.select("div.language-container:contains(Indonesian) div.subtitle-item, tr:contains(Indonesian)")

            indoElements.forEach { item ->
                // Ambil link download
                val linkEl = item.selectFirst("a.download-button, a")
                val dwnUrl = linkEl?.attr("href") ?: return@forEach
                val fullDwnUrl = if (dwnUrl.startsWith("http")) dwnUrl else "https://subsource.net$dwnUrl"
                
                // Ambil nama uploader/versi (misal: BluRay, Web-DL)
                val releaseName = item.select("span.release-name, td:nth-child(1)").text().trim()
                
                subList.add(
                    SubtitleFile(
                        lang = "Indonesian",
                        url = fullDwnUrl,
                        name = if (releaseName.isNotEmpty()) releaseName else "Indonesian Sub"
                    )
                )
            }
            return subList
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }
}
