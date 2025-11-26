package com.pusatfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Pusatfilm : MainAPI() {

    override var mainUrl = "https://pusatfilm21.online"
    override var name = "Pusatfilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "film-terbaru/page/%d/" to "Film Terbaru",
        "trending/page/%d/" to "Film Trending",
        "genre/action/page/%d/" to "Film Action",
        "series-terbaru/page/%d/" to "Series Terbaru",
        "drama-korea/page/%d/" to "Drama Korea",
        "west-series/page/%d/" to "West Series",
        "drama-china/page/%d/" to "Drama China",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data.format(page)
        val document = app.get("$mainUrl/$data").document
        val home = document.select("article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.getImageAttr()).fixImageQuality()
        
        // Deteksi Series vs Movie berdasarkan URL (Lebih akurat untuk situs ini)
        val isSeries = href.contains("/tv/")
        
        return if (isSeries) {
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                // Kualitas biasanya ada di label, misal "Bluray"
                val quality = this@toSearchResult.select("div.gmr-qual, div.gmr-quality-item > a").text().trim().replace("-", "")
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}?s=$query&post_type[]=post&post_type[]=tv", timeout = 50L).document
        return document.select("article.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "Unknown Title"
        
        // FIX GAMBAR: Mengambil dari og:image (meta tag) agar pasti muncul di detail
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.fixImageQuality()
            ?: document.selectFirst("div.gmr-poster img")?.getImageAttr()?.fixImageQuality()

        val tags = document.select("div.gmr-movie-genre a").map { it.text() }
        val year = document.selectFirst("div.gmr-movie-date a")?.text()?.toIntOrNull()
        val plot = document.selectFirst("div.entry-content p")?.text()?.trim()
        
        val isSeries = url.contains("/tv/")

        return if (isSeries) {
            // FIX SERIES: Parsing episode lebih rapi dan diurutkan
            val episodes = document.select("div.gmr-listseries a").mapNotNull { eps ->
                val href = fixUrl(eps.attr("href"))
                val rawTitle = eps.attr("title") // Judul mentah "Nonton Series..."
                
                // Ambil angka episode menggunakan Regex
                val episodeMatch = Regex("(?i)Episode\\s*(\\d+)").find(rawTitle)
                val episodeNum = episodeMatch?.groupValues?.get(1)?.toIntOrNull()
                
                // Jika tidak ketemu angka episode, lewati (karena mungkin bukan link episode valid)
                if (episodeNum == null) return@mapNotNull null

                newEpisode(href) {
                    this.name = "Episode $episodeNum" // Paksa nama jadi rapi
                    this.episode = episodeNum
                }
            }.sortedBy { it.episode } // Urutkan episode dari 1 ke atas

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // 1. Cek Dropdown Server
        document.select("ul#dropdown-server li a").forEach {
            val encodedUrl = it.attr("data-frame")
            if (encodedUrl.isNotEmpty()) {
                val decodedUrl = base64Decode(encodedUrl)
                loadExtractor(decodedUrl, data, subtitleCallback, callback)
            }
        }
        
        // 2. Cek Iframe Utama
        document.select("div.gmr-embed-responsive iframe").forEach {
            val src = it.attr("src")
            if (src.isNotEmpty() && !src.contains("youtube")) {
                 loadExtractor(src, data, subtitleCallback, callback)
            }
        }

        return true
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }

    private fun String?.fixImageQuality(): String? {
        if (this == null) return null
        val regex = Regex("(-\\d*x\\d*)").find(this)?.groupValues?.get(0) ?: return this
        return this.replace(regex, "")
    }
}
