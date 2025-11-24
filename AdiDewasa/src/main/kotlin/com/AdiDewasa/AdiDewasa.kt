package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AdiDewasa : MainAPI() {
    override var mainUrl = "https://dramafull.cc"
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

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
                        // Packing Data menggunakan AdiLinkInfo yang ada di AdiDewasaExtractor
                        val dataJson = AdiLinkInfo(episodeHref, title, year, episodeNum, 1).toJson()
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        // Parse data
        val info = try {
            AppUtils.parseJson<AdiLinkInfo>(data)
        } catch (e: Exception) {
            AdiLinkInfo(data, "", null)
        }

        // Panggil Extractor dari file terpisah
        AdiDewasaExtractor.invokeAll(info, subtitleCallback, callback)

        return true
    }
}
