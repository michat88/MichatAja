package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AdiDewasa : MainAPI() {
    override var mainUrl = "https://dramafull.cc"
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    override val mainPage: List<MainPageData>
        get() = listOf(
            MainPageData("Adult Movies - Baru Rilis", "2:1:adult"),
            MainPageData("Adult Movies - Paling Populer", "2:5:adult"),
            MainPageData("Adult Movies - Rating Tertinggi", "2:6:adult"),
            MainPageData("Adult TV Shows - Episode Baru", "1:1:adult"),
            MainPageData("All Collections - Rekomendasi", "-1:6:adult")
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val dataParts = request.data.split(":")
            val type = dataParts.getOrNull(0) ?: "-1"
            val sort = dataParts.getOrNull(1)?.toIntOrNull() ?: 1
            val adultFlag = dataParts.getOrNull(2) ?: "normal"
            val isAdultSection = adultFlag == "adult"

            val jsonPayload = """{
                "page": $page,
                "type": "$type",
                "country": -1,
                "sort": $sort,
                "adult": true,
                "adultOnly": $isAdultSection,
                "ignoreWatched": false,
                "genres": [],
                "keyword": ""
            }""".trimIndent()

            val payload = jsonPayload.toRequestBody("application/json".toMediaType())
            val response = app.post("$mainUrl/api/filter", requestBody = payload)
            
            val homeResponse = response.parsedSafe<HomeResponse>()
            
            if (homeResponse?.success == false) {
                return newHomePageResponse(emptyList(), hasNext = false)
            }

            val mediaList = homeResponse?.data ?: emptyList()
            val searchResults = mediaList.mapNotNull { it.toSearchResult() }

            return newHomePageResponse(
                list = HomePageList(request.name, searchResults, false),
                hasNext = homeResponse?.nextPageUrl != null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return newHomePageResponse(emptyList(), hasNext = false)
        }
    }

    private fun MediaItem.toSearchResult(): SearchResponse? {
        val itemTitle = this.title ?: this.name ?: return null
        val itemSlug = this.slug ?: return null
        val itemImage = this.image?.takeIf { it.isNotEmpty() } ?: this.poster

        val href = "$mainUrl/film/$itemSlug"
        val posterUrl = if (!itemImage.isNullOrEmpty()) {
            if (itemImage.startsWith("http")) itemImage else mainUrl + itemImage
        } else null

        return newMovieSearchResponse(itemTitle, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl/api/live-search/$query"
        return try {
            app.get(url).parsedSafe<ApiSearchResponse>()?.data?.mapNotNull { it.toSearchResult() }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document

        // --- TITLE CLEANER (Hanya ini yang kita ubah dari file asli untuk load) ---
        val ogTitle = document.select("meta[property=og:title]").attr("content")
        val fallbackTitle = document.selectFirst("h1")?.text() ?: "Unknown Title"
        val rawTitle = if (ogTitle.isNotEmpty()) ogTitle else fallbackTitle

        // Membersihkan judul sampah
        val title = rawTitle
            .replace(Regex("(?i)^Watch\\s+"), "")
            .substringBefore(" - Movie")
            .substringBefore(" subbed")
            .substringBefore(" online")
            .trim()
        // -------------------------------------------------------------------------

        val poster = document.select("meta[property=og:image]").attr("content")
        val desc = document.select("meta[property=og:description]").attr("content")
        
        var year = Regex("\\d{4}").find(title)?.value?.toIntOrNull()
        if (year == null) year = Regex("\\d{4}").find(document.text())?.value?.toIntOrNull()
        
        val tags = document.select("div.genre-list a, .genres a").map { it.text() }

        // --- LOGIC ORIGINAL UNTUK EPISODE ---
        val episodes = document.select(".episode-list a, .episode-item a, ul.episodes li a").mapNotNull {
            val text = it.text()
            val href = it.attr("href")
            val epNum = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() 
                ?: Regex("""Episode\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            
            if (epNum == null) return@mapNotNull null
            
            // PENTING: Kirim URL mentah (href), JANGAN JSON.
            newEpisode(href) { 
                this.name = "Episode $epNum"
                this.episode = epNum
            }
        }

        val recommendations = document.select("div.film_list-wrap div.flw-item").mapNotNull {
            val recTitle = it.select("h3.film-name a").text()
            val recHref = it.select("h3.film-name a").attr("href")
            val recPoster = it.select("img.film-poster-img").attr("data-src")
            if (recTitle.isEmpty()) return@mapNotNull null
            newMovieSearchResponse(recTitle, "$mainUrl$recHref", TvType.Movie) {
                this.posterUrl = recPoster
            }
        }

        val tvType = if (episodes.isNotEmpty()) TvType.TvSeries else TvType.Movie
        
        // PENTING: Untuk Movie, kirim 'url' halaman ini langsung sebagai data.
        // Ini memastikan loadLinks menerima URL valid, bukan JSON.
        val dataUrl = if (tvType == TvType.Movie) url else ""

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, tvType, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
                this.tags = tags
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, tvType, dataUrl) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
                this.tags = tags
                this.recommendations = recommendations
            }
        }
    }

    // --- ORIGINAL LOAD LINKS (DIKEMBALIKAN KE VERSI FILE ASLI ANDA) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Data di sini adalah URL (String), karena kita mengembalikannya ke versi asli.
        if (data.isEmpty()) return false

        try {
            val doc = app.get(data).document
            
            // Mencari script signedUrl persis seperti file asli
            val script = doc.select("script:containsData(signedUrl)").firstOrNull()?.toString() ?: return false
            
            val signedUrl = Regex("""window\.signedUrl\s*=\s*"(.+?)"""").find(script)?.groupValues?.get(1)?.replace("\\/", "/") 
                ?: return false
            
            // Request ke API Player
            val res = app.get(signedUrl, referer = data).text
            val resJson = JSONObject(res)
            val videoSource = resJson.optJSONObject("video_source") ?: return false
            
            // Mengambil kualitas
            val qualities = videoSource.keys().asSequence().toList()
                .sortedByDescending { it.toIntOrNull() ?: 0 }
                
            val bestQualityKey = qualities.firstOrNull() ?: return false
            val bestQualityUrl = videoSource.optString(bestQualityKey)

            if (bestQualityUrl.isNotEmpty()) {
                // Return Link
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name, // Nama Source
                        bestQualityUrl,
                        com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
                    )
                )
                
                // Return Subtitle
                val subJson = resJson.optJSONObject("sub")
                subJson?.optJSONArray(bestQualityKey)?.let { array ->
                    for (i in 0 until array.length()) {
                        val subUrl = array.getString(i)
                        val finalSubUrl = if (subUrl.startsWith("http")) subUrl else "$mainUrl$subUrl"
                        subtitleCallback.invoke(newSubtitleFile("English", finalSubUrl))
                    }
                }
                
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return false
    }
}
