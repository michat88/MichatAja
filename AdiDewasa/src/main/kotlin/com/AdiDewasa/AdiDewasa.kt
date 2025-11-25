package com.AdiDewasa

import com.AdiDewasa.AdiDewasaExtractor.invokeAdiDewasaDirect
import com.AdiDewasa.AdiDewasaExtractor.invokeIdlix
import com.AdiDewasa.AdiDewasaExtractor.invokeMapple
import com.AdiDewasa.AdiDewasaExtractor.invokeSuperembed
import com.AdiDewasa.AdiDewasaExtractor.invokeVidfast
import com.AdiDewasa.AdiDewasaExtractor.invokeVidlink
import com.AdiDewasa.AdiDewasaExtractor.invokeVidsrc
import com.AdiDewasa.AdiDewasaExtractor.invokeVixsrc
import com.AdiDewasa.AdiDewasaExtractor.invokeWyzie
import com.AdiDewasa.AdiDewasaExtractor.invokeXprime
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AdiDewasa : MainAPI() {
    override var mainUrl = "https://dramafull.cc"
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    // Interceptor untuk menembus Cloudflare
    private val cfInterceptor by lazy { CloudflareKiller() }

    // Header browser lengkap agar tidak diblokir
    private val webHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "en-US,en;q=0.9",
        "Origin" to mainUrl,
        "Referer" to "$mainUrl/"
    )

    // Menggunakan Kategori dari File Asli (API Based)
    override val mainPage: List<MainPageData>
        get() = listOf(
            MainPageData("Adult Movies - Baru Rilis", "2:1:adult"),
            MainPageData("Adult Movies - Paling Populer", "2:5:adult"),
            MainPageData("Adult Movies - Rating Tertinggi", "2:6:adult"),
            MainPageData("Adult Movies - Sesuai Abjad (A-Z)", "2:3:adult"),
            MainPageData("Adult Movies - Klasik (Oldest)", "2:2:adult"),
            MainPageData("Adult TV Shows - Episode Baru", "1:1:adult"),
            MainPageData("Adult TV Shows - Paling Populer", "1:5:adult"),
            MainPageData("All Collections - Baru Ditambahkan", "-1:1:adult"),
            MainPageData("All Collections - Paling Sering Ditonton", "-1:5:adult")
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            // Logika API dari File Asli
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

            // Menggunakan cfInterceptor untuk keamanan koneksi
            val response = app.post(
                "$mainUrl/api/filter", 
                requestBody = payload, 
                headers = webHeaders,
                interceptor = cfInterceptor
            )
            
            val homeResponse = response.parsedSafe<HomeResponse>()
            val mediaList = homeResponse?.data ?: emptyList()
            
            val searchResults = mediaList.mapNotNull { it.toSearchResult() }

            return newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = searchResults,
                    isHorizontalImages = false
                ),
                hasNext = homeResponse?.nextPageUrl != null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback kosong jika gagal
            return newHomePageResponse(emptyList(), hasNext = false)
        }
    }

    // Helper untuk konversi data API ke SearchResponse
    private fun MediaItem.toSearchResult(): SearchResponse? {
        val itemTitle = this.title ?: this.name ?: return null
        val itemSlug = this.slug ?: return null
        // Menggunakan image atau poster, prioritaskan yang ada
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
        // Menggunakan API Live Search dari file asli (Lebih Cepat)
        val url = "$mainUrl/api/live-search/$query"
        return try {
            app.get(url, headers = webHeaders, interceptor = cfInterceptor)
                .parsedSafe<ApiSearchResponse>()?.data?.mapNotNull { it.toSearchResult() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun load(url: String): LoadResponse {
        // Logika Scraping HTML (Gabungan)
        val document = app.get(url, headers = webHeaders, interceptor = cfInterceptor).document
        
        val title = document.selectFirst("h1.heading-title, .film-info h1, .right-info h1")?.text()?.trim() ?: "Unknown Title"
        val poster = document.selectFirst(".film-poster img, .poster img")?.attr("src")
        val desc = document.selectFirst(".description, .film-desc, .story, p.summary-content")?.text()?.trim()
        val year = Regex("\\d{4}").find(document.text())?.value?.toIntOrNull()
        
        // Deteksi Episode
        val episodes = document.select(".episode-list a, .episode-item a, ul.episodes li a").mapNotNull {
            val text = it.text()
            val href = it.attr("href")
            // Regex ganda untuk menangkap "Episode 1" atau angka "1" saja
            val epNum = Regex("""Episode\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            
            if (epNum == null) return@mapNotNull null
            
            newEpisode(LinkData(
                url = fixUrl(href), // URL detail episode
                title = title,      // Judul Series
                year = year,
                season = 1,
                episode = epNum
            )) {
                this.name = "Episode $epNum"
                this.episode = epNum
            }
        }

        val tags = document.select("div.genre-list a, .genres a").map { it.text() }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
                this.tags = tags
            }
        } else {
            // Untuk Movie, LinkData berisi URL halaman ini sendiri
            newMovieLoadResponse(title, url, TvType.Movie, LinkData(url, title, year)) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
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
        
        // Parsing data LinkData yang dikirim dari load()
        val res = parseJson<LinkData>(data)

        // STRATEGI GABUNGAN: Internal Player + Eksternal Extractors
        runAllAsync(
            // 1. Player Internal Dramafull (Metode signedUrl dari file asli)
            {
                invokeAdiDewasaDirect(res.url, callback, subtitleCallback)
            },
            // 2. Extractor Cadangan (Metode AdiDrakor Modifikasi)
            { 
                invokeIdlix(res.title, res.year, res.season, res.episode, subtitleCallback, callback) 
            },
            { 
                invokeVidsrc(null, res.season, res.episode, subtitleCallback, callback) 
            },
            { 
                invokeXprime(null, res.title, res.year, res.season, res.episode, subtitleCallback, callback) 
            },
            { 
                invokeVidfast(null, res.season, res.episode, subtitleCallback, callback) 
            },
            { 
                invokeVidlink(null, res.season, res.episode, callback) 
            },
            { 
                invokeMapple(null, res.season, res.episode, subtitleCallback, callback) 
            },
            { 
                invokeWyzie(null, res.season, res.episode, subtitleCallback) 
            },
            { 
                invokeVixsrc(null, res.season, res.episode, callback) 
            },
            { 
                invokeSuperembed(null, res.season, res.episode, subtitleCallback, callback) 
            }
        )

        return true
    }
}
