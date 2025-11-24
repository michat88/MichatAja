package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson // ERROR FIX: Import Extension Function
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.SubtitleFile // ERROR FIX: Import Class langsung
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

class AdiDewasa : MainAPI() {
    override var mainUrl = "https://dramafull.cc"
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    // --- MAIN PAGE CONFIGURATION ---
    override val mainPage: List<MainPageData>
        get() {
            return listOf(
                MainPageData("Adult Movies - Baru Rilis", "2:1:adult"),
                MainPageData("Adult Movies - Paling Populer", "2:5:adult"),
                MainPageData("Adult Movies - Rating Tertinggi", "2:6:adult"),
                MainPageData("Adult TV Shows - Episode Baru", "1:1:adult"),
                MainPageData("Adult TV Shows - Paling Populer", "1:5:adult"),
                MainPageData("All Collections - Rekomendasi Terbaik", "-1:6:adult")
            )
        }

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
            
            if (homeResponse?.success == false) return newHomePageResponse(emptyList(), hasNext = false)

            val mediaList = homeResponse?.data ?: emptyList()
            val searchResults = mediaList.mapNotNull { it.toSearchResult() }

            return newHomePageResponse(
                list = HomePageList(request.name, searchResults, isHorizontalImages = false),
                hasNext = homeResponse?.nextPageUrl != null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return newHomePageResponse(emptyList(), hasNext = false)
        }
    }

    private fun MediaItem.toSearchResult(): SearchResponse? {
        try {
            val itemTitle = this.title ?: this.name ?: "Unknown Title"
            val itemSlug = this.slug ?: return null
            val itemImage = this.image ?: this.poster ?: ""
            val href = "$mainUrl/film/$itemSlug"
            val posterUrl = if (itemImage.isNotEmpty()) {
                if (itemImage.startsWith("http")) itemImage else mainUrl + itemImage
            } else ""

            return newMovieSearchResponse(itemTitle, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } catch (e: Exception) { return null }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        try {
            val url = "$mainUrl/api/live-search/$query"
            val response = app.get(url)
            val searchResponse = response.parsedSafe<ApiSearchResponse>()
            return searchResponse?.data?.mapNotNull { it.toSearchResult() }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // --- LOAD FUNCTION (MODIFIED FOR HYBRID DATA) ---
    override suspend fun load(url: String): LoadResponse {
        try {
            val doc = app.get(url).document
            val title = doc.selectFirst("div.right-info h1, h1.title")?.text() ?: "Unknown"
            val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
            val genre = doc.select("div.genre-list a, .genres a").map { it.text() }
            val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()
            val description = doc.selectFirst("div.right-info p.summary-content, .summary p")?.text() ?: ""
            
            val hasEpisodes = doc.select("div.tab-content.episode-button, .episodes-list").isNotEmpty()
            val type = if (hasEpisodes) TvType.TvSeries else TvType.Movie
            val videoHref = doc.selectFirst("div.last-episode a, .watch-button a")?.attr("href") ?: url

            val recs = doc.select("div.film_list-wrap div.flw-item, .recommendations .item").mapNotNull {
                val rTitle = it.selectFirst("img")?.attr("alt") ?: it.selectFirst("h3, .title")?.text() ?: ""
                val rImage = it.selectFirst("img")?.attr("data-src") ?: it.selectFirst("img")?.attr("src") ?: ""
                val rHref = it.selectFirst("a")?.attr("href") ?: ""
                if (rTitle.isNotEmpty() && rHref.isNotEmpty()) {
                    newMovieSearchResponse(rTitle, rHref, TvType.Movie) {
                        this.posterUrl = if (rImage.startsWith("http")) rImage else mainUrl + rImage
                    }
                } else null
            }

            if (type == TvType.TvSeries) {
                val episodes = doc.select("div.episode-item a, .episode-list a").mapNotNull {
                    val episodeText = it.text().trim()
                    val episodeHref = it.attr("href")
                    val episodeNum = Regex("""Episode\s*(\d+)""", RegexOption.IGNORE_CASE)
                        .find(episodeText)?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("""(\d+)""").find(episodeText)?.groupValues?.get(1)?.toIntOrNull()

                    if (episodeHref.isNotEmpty()) {
                        // ERROR FIX: Gunakan .toJson() pada objek, bukan AppUtils.toJson(objek)
                        val dataJson = AdiLinkInfo(episodeHref, title, year, episodeNum, 1).toJson()
                        
                        // ERROR FIX: Gunakan .apply {} untuk menghindari Type Mismatch (expected Boolean)
                        newEpisode(dataJson).apply {
                            this.name = "Episode ${episodeNum ?: episodeText}"
                            this.episode = episodeNum
                        }
                    } else null
                }
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.year = year
                    this.tags = genre
                    this.posterUrl = poster
                    this.plot = description
                    this.recommendations = recs
                }
            } else {
                // ERROR FIX: Gunakan .toJson()
                val dataJson = AdiLinkInfo(videoHref, title, year).toJson()
                
                return newMovieLoadResponse(title, url, TvType.Movie, dataJson) {
                    this.year = year
                    this.tags = genre
                    this.posterUrl = poster
                    this.plot = description
                    this.recommendations = recs
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    // --- LOAD LINKS (HYBRID: TMDB + WYZIE + ORIGINAL) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        // 1. Parse Data JSON
        val info = try {
            AppUtils.parseJson<AdiLinkInfo>(data)
        } catch (e: Exception) {
            // Fallback untuk kompatibilitas lama
            AdiLinkInfo(data, "", null)
        }
        val targetUrl = info.url

        // 2. HYBRID LOGIC: Cari ID TMDB & Subtitle Wyzie
        if (info.title.isNotEmpty()) {
            val isMovie = info.season == null
            val tmdbId = getTmdbId(info.title, info.year, isMovie)
            
            if (tmdbId != null) {
                // Panggil Wyzie Subtitle
                val wyzieUrl = if (isMovie) 
                    "https://sub.wyzie.ru/search?id=$tmdbId"
                else 
                    "https://sub.wyzie.ru/search?id=$tmdbId&season=${info.season}&episode=${info.episode}"
                
                try {
                    val res = app.get(wyzieUrl).text
                    val jsonArr = org.json.JSONArray(res)
                    for (i in 0 until jsonArr.length()) {
                        val item = jsonArr.getJSONObject(i)
                        // ERROR FIX: Gunakan SubtitleFile langsung (ganti newSubtitleFile)
                        subtitleCallback(
                            SubtitleFile(item.getString("display"), item.getString("url"))
                        )
                    }
                } catch (e: Exception) { /* Ignore wyzie errors */ }
            }
        }

        // 3. ORIGINAL LOGIC: Ambil Video dari Dramafull
        try {
            val doc = app.get(targetUrl).document
            val script = doc.select("script:containsData(signedUrl)").firstOrNull()?.toString() ?: return false
            
            val signedUrl = Regex("""window\.signedUrl\s*=\s*["'](.+?)["']""").find(script)?.groupValues?.get(1)?.replace("\\/", "/") 
                ?: return false
            
            val res = app.get(signedUrl).text
            val resJson = JSONObject(res)
            val videoSource = resJson.optJSONObject("video_source") ?: return false
            
            val qualities = videoSource.keys().asSequence().toList()
                .sortedByDescending { it.toIntOrNull() ?: 0 }
            val bestQualityKey = qualities.firstOrNull() ?: return false
            val bestQualityUrl = videoSource.optString(bestQualityKey)

            if (bestQualityUrl.isNotEmpty()) {
                callback(
                    newExtractorLink(
                        name,
                        name,
                        bestQualityUrl,
                        com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
                    )
                )
                
                // Subtitle bawaan Dramafull (Backup)
                val subJson = resJson.optJSONObject("sub")
                subJson?.optJSONArray(bestQualityKey)?.let { array ->
                    for (i in 0 until array.length()) {
                        val subUrl = array.getString(i)
                        // ERROR FIX: Gunakan SubtitleFile langsung
                        subtitleCallback(SubtitleFile("English (Original)", mainUrl + subUrl))
                    }
                }
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return false
    }

    // --- HELPER FUNCTIONS FOR HYBRID SYSTEM ---

    data class AdiLinkInfo(
        val url: String,
        val title: String,
        val year: Int?,
        val episode: Int? = null,
        val season: Int? = null
    )

    data class TmdbSearch(val results: List<TmdbRes>?)
    data class TmdbRes(val id: Int?)

    private suspend fun getTmdbId(title: String, year: Int?, isMovie: Boolean): Int? {
        try {
            val apiKey = "b030404650f279792a8d3287232358e3" // Cloudstream Public Key
            val type = if (isMovie) "movie" else "tv"
            val q = URLEncoder.encode(title, "UTF-8")
            
            // Prioritas 1: Cari dengan Tahun
            if (year != null) {
                val url = "https://api.themoviedb.org/3/search/$type?api_key=$apiKey&query=$q&year=$year"
                val res = app.get(url).parsedSafe<TmdbSearch>()?.results?.firstOrNull()?.id
                if (res != null) return res
            }
            
            // Prioritas 2: Cari tanpa Tahun
            val urlNoYear = "https://api.themoviedb.org/3/search/$type?api_key=$apiKey&query=$q"
            return app.get(urlNoYear).parsedSafe<TmdbSearch>()?.results?.firstOrNull()?.id
        } catch (e: Exception) {
            return null
        }
    }
}
