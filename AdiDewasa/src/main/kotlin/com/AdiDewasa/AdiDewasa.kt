package com.AdiDewasa

import com.AdiDewasa.AdiDewasaExtractor.invokeAdiDewasaDirect
import com.AdiDewasa.AdiDewasaExtractor.invokeIdlix
import com.AdiDewasa.AdiDewasaExtractor.invokeMapple
import com.AdiDewasa.AdiDewasaExtractor.invokeSuperembed
import com.AdiDewasa.AdiDewasaExtractor.invokeVidfast
import com.AdiDewasa.AdiDewasaExtractor.invokeVidlink
import com.AdiDewasa.AdiDewasaExtractor.invokeVidsrc
import com.AdiDewasa.AdiDewasaExtractor.invokeVidsrccc
import com.AdiDewasa.AdiDewasaExtractor.invokeVixsrc
import com.AdiDewasa.AdiDewasaExtractor.invokeWyzie
import com.AdiDewasa.AdiDewasaExtractor.invokeXprime
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Element
import java.net.URLEncoder

class AdiDewasa : MainAPI() {
    override var name = "AdiDewasa"
    override var mainUrl = "https://dramafull.cc"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true

    // FITUR PENTING: Penembus Cloudflare
    private val cfInterceptor by lazy { CloudflareKiller() }

    // Header browser untuk menyamar
    private val webHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "Referer" to "$mainUrl/"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/movies" to "Latest Movies",
        "$mainUrl/popular" to "Popular",
        "$mainUrl/genre/romance" to "Romance",
        "$mainUrl/genre/drama" to "Drama",
        "$mainUrl/genre/action" to "Action"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if(page == 1) request.data else "${request.data}?page=$page"
        
        // REVISI: Menggunakan interceptor CloudflareKiller
        val response = app.get(url, headers = webHeaders, interceptor = cfInterceptor)
        val document = response.document
        
        // Scraping dengan selector yang lebih agresif
        val home = document.select("div.item, .film-item, a.poster, .movie-item, .post-item").mapNotNull {
            it.toSearchResult()
        }
        
        if (home.isEmpty()) {
            throw ErrorLoadingException("Daftar kosong. Coba 'Buka di Peramban' dulu untuk verifikasi Cloudflare.")
        }
        
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst(".title, h3, .name, .entry-title")?.text()?.trim() 
            ?: this.attr("title").ifEmpty { null }
            ?: return null
        
        var href = this.selectFirst("a")?.attr("href") ?: this.attr("href")
        if (href.isEmpty()) return null
        href = fixUrl(href)

        val imgTag = this.selectFirst("img")
        val poster = imgTag?.attr("data-src") 
            ?: imgTag?.attr("data-original") 
            ?: imgTag?.attr("src")

        val quality = this.selectFirst(".quality, .ep, .label, .ribbon")?.text()?.trim()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
            if(quality != null) addQuality(quality)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        val cleanQuery = AdiDewasaHelper.normalizeQuery(query)
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
        val url = "$mainUrl/api/live-search/$encodedQuery"
        
        // Search API biasanya jarang kena Cloudflare, tapi kita pakai header JSON yang benar
        return app.get(url, headers = AdiDewasaHelper.headers).parsedSafe<DramaFullSearchResponse>()?.data?.map {
            newMovieSearchResponse(it.title ?: it.name ?: "", "$mainUrl/film/${it.slug}", TvType.Movie) {
                this.posterUrl = it.image
                this.year = it.year?.toIntOrNull()
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        // Load detail juga menggunakan interceptor Cloudflare
        val document = app.get(url, headers = webHeaders, interceptor = cfInterceptor).document
        
        val title = document.selectFirst("h1.heading-title, .film-info h1")?.text()?.trim() ?: "Unknown Title"
        val poster = document.selectFirst(".film-poster img, .poster img")?.attr("src")
        val desc = document.selectFirst(".description, .film-desc, .story")?.text()?.trim()
        val year = Regex("\\d{4}").find(document.text())?.value?.toIntOrNull()
        
        val episodes = document.select(".episode-list a, .episode-item a, ul.episodes li a").mapNotNull {
            val epNum = Regex("(\\d+)").find(it.text())?.groupValues?.get(1)?.toIntOrNull()
            val href = it.attr("href")
            if (epNum == null) return@mapNotNull null
            
            newEpisode(LinkData(fixUrl(href), title, year, 1, epNum)) {
                this.name = "Episode $epNum"
                this.episode = epNum
            }
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, LinkData(url, title, year)) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<LinkData>(data)

        runAllAsync(
            { invokeAdiDewasaDirect(res.url, callback, subtitleCallback) },
            { invokeIdlix(res.title, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokeVidsrc(null, res.season, res.episode, subtitleCallback, callback) },
            { invokeXprime(null, res.title, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokeVidfast(null, res.season, res.episode, subtitleCallback, callback) },
            { invokeVidlink(null, res.season, res.episode, callback) },
            { invokeMapple(null, res.season, res.episode, subtitleCallback, callback) },
            { invokeWyzie(null, res.season, res.episode, subtitleCallback) },
            { invokeVixsrc(null, res.season, res.episode, callback) },
            { invokeSuperembed(null, res.season, res.episode, subtitleCallback, callback) }
        )
        return true
    }
}
