package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.net.URLEncoder

object AdiDewasaExtractor : AdiDewasa() {

    // ================== 1. ADIDEWASA (DRAMAFULL) ==================
    @Suppress("UNCHECKED_CAST") 
    suspend fun invokeAdiDewasa(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val baseUrl = "https://dramafull.cc"
        val cleanQuery = AdiDewasaHelper.normalizeQuery(title)
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8").replace("+", "%20")
        val searchUrl = "$baseUrl/api/live-search/$encodedQuery"

        try {
            val searchRes = app.get(searchUrl, headers = AdiDewasaHelper.headers).parsedSafe<AdiDewasaSearchResponse>()
            
            val matchedItem = searchRes?.data?.find { item ->
                val itemTitle = item.title ?: item.name ?: ""
                AdiDewasaHelper.isFuzzyMatch(title, itemTitle)
            } ?: searchRes?.data?.firstOrNull()

            if (matchedItem == null) return 

            val slug = matchedItem.slug ?: return
            var targetUrl = "$baseUrl/film/$slug"

            val doc = app.get(targetUrl, headers = AdiDewasaHelper.headers).document

            if (season != null && episode != null) {
                val episodeHref = doc.select("div.episode-item a, .episode-list a").find { 
                    val text = it.text().trim()
                    val epNum = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    epNum == episode
                }?.attr("href")
                if (episodeHref == null) return
                targetUrl = fixUrl(episodeHref, baseUrl)
            } else {
                val selectors = listOf("a.btn-watch", "a.watch-now", ".watch-button a", "div.last-episode a", ".film-buttons a.btn-primary")
                for (selector in selectors) {
                    val el = doc.selectFirst(selector)
                    if (el != null) {
                        val href = el.attr("href")
                        if (href.isNotEmpty() && !href.contains("javascript") && href != "#") {
                            targetUrl = fixUrl(href, baseUrl); break
                        }
                    }
                }
            }

            val docPage = app.get(targetUrl, headers = AdiDewasaHelper.headers).document
            val allScripts = docPage.select("script").joinToString(" ") { it.data() }
            val signedUrl = Regex("""signedUrl\s*=\s*["']([^"']+)["']""").find(allScripts)?.groupValues?.get(1)?.replace("\\/", "/") ?: return
            
            val jsonResponseText = app.get(signedUrl, referer = targetUrl, headers = AdiDewasaHelper.headers).text
            val jsonObject = tryParseJson<Map<String, Any>>(jsonResponseText) ?: return
            val videoSource = jsonObject["video_source"] as? Map<String, String> ?: return
            
            videoSource.forEach { (quality, url) ->
                 if (url.isNotEmpty()) {
                    callback.invoke(
                        newExtractorLink(
                            "AdiDewasa",
                            "AdiDewasa ($quality)",
                            url,
                            INFER_TYPE 
                        )
                    )
                }
            }
             val bestQualityKey = videoSource.keys.maxByOrNull { it.toIntOrNull() ?: 0 } ?: return
             val subJson = jsonObject["sub"] as? Map<String, Any>
             val subs = subJson?.get(bestQualityKey) as? List<String>
             subs?.forEach { subPath ->
                 subtitleCallback.invoke(newSubtitleFile("English", fixUrl(subPath, baseUrl)))
             }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ================== 2. ADIMOVIEBOX ==================
    suspend fun invokeAdimoviebox(title: String, year: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val searchUrl = "https://moviebox.ph/wefeed-h5-bff/web/subject/search"
        val streamApi = "https://fmoviesunblocked.net"
        val searchBody = mapOf("keyword" to title, "page" to 1, "perPage" to 10, "subjectType" to 0).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        val searchRes = app.post(searchUrl, requestBody = searchBody).text
        val items = tryParseJson<AdimovieboxSearch>(searchRes)?.data?.items ?: return
        val matchedMedia = items.find { item ->
            val itemYear = item.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
            (item.title.equals(title, true)) || (item.title?.contains(title, true) == true && itemYear == year)
        } ?: return
        val subjectId = matchedMedia.subjectId ?: return
        val se = season ?: 0; val ep = episode ?: 0
        val playUrl = "$streamApi/wefeed-h5-bff/web/subject/play?subjectId=$subjectId&se=$se&ep=$ep"
        val validReferer = "$streamApi/spa/videoPlayPage/movies/${matchedMedia.detailPath}?id=$subjectId&type=/movie/detail&lang=en"
        val playRes = app.get(playUrl, referer = validReferer).text
        val streams = tryParseJson<AdimovieboxStreams>(playRes)?.data?.streams ?: return
        streams.reversed().forEach { source ->
             callback.invoke(newExtractorLink("Adimoviebox", "Adimoviebox", source.url ?: return@forEach, INFER_TYPE) {
                    this.referer = validReferer; this.quality = getQualityFromName(source.resolutions)
                })
        }
        val id = streams.firstOrNull()?.id; val format = streams.firstOrNull()?.format
        if (id != null) {
            val subUrl = "$streamApi/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=$subjectId"
            app.get(subUrl, referer = validReferer).parsedSafe<AdimovieboxCaptions>()?.data?.captions?.forEach { sub ->
                subtitleCallback.invoke(newSubtitleFile(sub.lanName ?: "Unknown", sub.url ?: return@forEach))
            }
        }
    }
    data class AdimovieboxSearch(val data: AdimovieboxData?)
    data class AdimovieboxData(val items: List<AdimovieboxItem>?)
    data class AdimovieboxItem(val subjectId: String?, val title: String?, val releaseDate: String?, val detailPath: String?)
    data class AdimovieboxStreams(val data: AdimovieboxStreamData?)
    data class AdimovieboxStreamData(val streams: List<AdimovieboxStreamItem>?)
    data class AdimovieboxStreamItem(val id: String?, val format: String?, val url: String?, val resolutions: String?)
    data class AdimovieboxCaptions(val data: AdimovieboxCaptionData?)
    data class AdimovieboxCaptionData(val captions: List<AdimovieboxCaptionItem>?)
    data class AdimovieboxCaptionItem(val lanName: String?, val url: String?)

    // ================== 3. IDLIX (JENIUSPLAY) ==================
    suspend fun invokeIdlix(title: String? = null, year: Int? = null, season: Int? = null, episode: Int? = null, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val fixTitle = title?.createSlug()
        val url = if (season == null) "$idlixAPI/movie/$fixTitle-$year" else "$idlixAPI/episode/$fixTitle-season-$season-episode-$episode"
        invokeWpmovies("Idlix", url, subtitleCallback, callback, encrypt = true)
    }

    private suspend fun invokeWpmovies(name: String? = null, url: String? = null, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit, fixIframe: Boolean = false, encrypt: Boolean = false, hasCloudflare: Boolean = false, interceptor: Interceptor? = null) {
        val res = app.get(url ?: return, interceptor = if (hasCloudflare) interceptor else null)
        val referer = getBaseUrl(res.url)
        val document = res.document
        document.select("ul#playeroptionsul > li").map { Triple(it.attr("data-post"), it.attr("data-nume"), it.attr("data-type")) }.amap { (id, nume, type) ->
            val json = app.post(url = "$referer/wp-admin/admin-ajax.php", data = mapOf("action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type), headers = mapOf("Accept" to "*/*", "X-Requested-With" to "XMLHttpRequest"), referer = url, interceptor = if (hasCloudflare) interceptor else null).text
            val source = tryParseJson<ResponseHash>(json)?.let {
                when {
                    encrypt -> {
                        val meta = tryParseJson<Map<String, String>>(it.embed_url)?.get("m") ?: return@amap
                        val key = generateWpKey(it.key ?: return@amap, meta)
                        AesHelper.cryptoAESHandler(it.embed_url, key.toByteArray(), false)?.fixUrlBloat()
                    }
                    fixIframe -> Jsoup.parse(it.embed_url).select("IFRAME").attr("SRC")
                    else -> it.embed_url
                }
            } ?: return@amap
            when {
                source.startsWith("https://jeniusplay.com") -> Jeniusplay2().getUrl(source, "$referer/", subtitleCallback, callback)
                !source.contains("youtube") -> loadExtractor(source, "$referer/", subtitleCallback, callback)
            }
        }
    }

    // ================== 4. VIDSRCCC ==================
    suspend fun invokeVidsrccc(tmdbId: Int?, imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val url = if (season == null) "$vidsrcccAPI/v2/embed/movie/$tmdbId" else "$vidsrcccAPI/v2/embed/tv/$tmdbId/$season/$episode"
        val script = app.get(url).document.selectFirst("script:containsData(userId)")?.data() ?: return
        val userId = script.substringAfter("userId = \"").substringBefore("\";")
        val vrf = VidsrcHelper.encryptAesCbc("$tmdbId", "secret_$userId")
        val v = script.substringAfter("v = \"").substringBefore("\";")
        val serverUrl = if (season == null) "$vidsrcccAPI/api/$tmdbId/servers?id=$tmdbId&type=movie&v=$v&vrf=$vrf&imdbId=$imdbId" else "$vidsrcccAPI/api/$tmdbId/servers?id=$tmdbId&type=tv&v=$v&vrf=$vrf&imdbId=$imdbId&season=$season&episode=$episode"
        app.get(serverUrl).parsedSafe<VidsrcccResponse>()?.data?.amap {
            val sources = app.get("$vidsrcccAPI/api/source/${it.hash}").parsedSafe<VidsrcccResult>()?.data ?: return@amap
            if (it.name.equals("VidPlay")) {
                callback.invoke(newExtractorLink("VidPlay", "VidPlay", sources.source ?: return@amap, ExtractorLinkType.M3U8) { this.referer = "$vidsrcccAPI/" })
                sources.subtitles?.map { s -> subtitleCallback.invoke(newSubtitleFile(s.label ?: return@map, s.file ?: return@map)) }
            }
        }
    }

    // ================== 5. VIDLINK ==================
    suspend fun invokeVidlink(tmdbId: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val type = if (season == null) "movie" else "tv"
        val url = if (season == null) "$vidlinkAPI/$type/$tmdbId" else "$vidlinkAPI/$type/$tmdbId/$season/$episode"
        val videoLink = app.get(url, interceptor = WebViewResolver(Regex("""$vidlinkAPI/api/b/$type/A{32}"""), timeout = 15_000L)).parsedSafe<VidlinkSources>()?.stream?.playlist
        callback.invoke(newExtractorLink("Vidlink", "Vidlink", videoLink ?: return, ExtractorLinkType.M3U8) { this.referer = "$vidlinkAPI/" })
    }

    // ================== 6. VIDSRC ==================
    suspend fun invokeVidsrc(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val api = "https://cloudnestra.com"
        val url = if (season == null) "$vidSrcAPI/embed/movie?imdb=$imdbId" else "$vidSrcAPI/embed/tv?imdb=$imdbId&season=$season&episode=$episode"
        app.get(url).document.select(".serversList .server").amap { server ->
            if (server.text().equals("CloudStream Pro", ignoreCase = true)) {
                val hash = app.get("$api/rcp/${server.attr("data-hash")}").text.substringAfter("/prorcp/").substringBefore("'")
                val res = app.get("$api/prorcp/$hash").text
                val m3u8Link = Regex("https:.*\\.m3u8").find(res)?.value
                callback.invoke(newExtractorLink("Vidsrc", "Vidsrc", m3u8Link ?: return@amap, ExtractorLinkType.M3U8))
            }
        }
    }

    // ================== 7. WATCHSOMUCH ==================
    suspend fun invokeWatchsomuch(imdbId: String? = null, season: Int? = null, episode: Int? = null, subtitleCallback: (SubtitleFile) -> Unit) {
        val id = imdbId?.removePrefix("tt")
        val epsId = app.post("${watchSomuchAPI}/Watch/ajMovieTorrents.aspx", data = mapOf("index" to "0", "mid" to "$id", "wsk" to "30fb68aa-1c71-4b8c-b5d4-4ca9222cfb45", "lid" to "", "liu" to ""), headers = mapOf("X-Requested-With" to "XMLHttpRequest")).parsedSafe<WatchsomuchResponses>()?.movie?.torrents?.let { eps ->
            if (season == null) eps.firstOrNull()?.id else eps.find { it.episode == episode && it.season == season }?.id
        } ?: return
        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
        val subUrl = if (season == null) "${watchSomuchAPI}/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part=" else "${watchSomuchAPI}/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part=S${seasonSlug}E${episodeSlug}"
        app.get(subUrl).parsedSafe<WatchsomuchSubResponses>()?.subtitles?.map { sub ->
            subtitleCallback.invoke(newSubtitleFile(sub.label?.substringBefore("&nbsp")?.trim() ?: "", fixUrl(sub.url ?: return@map null, watchSomuchAPI)))
        }
    }

    // ================== 8. MAPPLE ==================
    suspend fun invokeMapple(tmdbId: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val mediaType = if (season == null) "movie" else "tv"
        val url = if (season == null) "$mappleAPI/watch/$mediaType/$tmdbId" else "$mappleAPI/watch/$mediaType/$season-$episode/$tmdbId"
        val data = if (season == null) """[{"mediaId":$tmdbId,"mediaType":"$mediaType","tv_slug":"","source":"mapple","sessionId":"session_1760391974726_qym92bfxu"}]""" else """[{"mediaId":$tmdbId,"mediaType":"$mediaType","tv_slug":"$season-$episode","source":"mapple","sessionId":"session_1760391974726_qym92bfxu"}]"""
        val res = app.post(url, requestBody = data.toRequestBody(RequestBodyTypes.TEXT.toMediaTypeOrNull()), headers = mapOf("Next-Action" to "403f7ef15810cd565978d2ac5b7815bb0ff20258a5")).text
        val videoLink = tryParseJson<MappleSources>(res.substringAfter("1:").trim())?.data?.stream_url
        callback.invoke(newExtractorLink("Mapple", "Mapple", videoLink ?: return, ExtractorLinkType.M3U8) { this.referer = "$mappleAPI/"; this.headers = mapOf("Accept" to "*/*") })
        val subRes = app.get("$mappleAPI/api/subtitles?id=$tmdbId&mediaType=$mediaType${if (season == null) "" else "&season=1&episode=1"}", referer = "$mappleAPI/").text
        tryParseJson<ArrayList<MappleSubtitle>>(subRes)?.map { subtitle -> subtitleCallback.invoke(newSubtitleFile(subtitle.display ?: "", fixUrl(subtitle.url ?: return@map, mappleAPI))) }
    }

    // ================== 9. SUPEREMBED ==================
    suspend fun invokeSuperembed(tmdbId: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit, api: String = "https://streamingnow.mov") {
        val path = if (season == null) "" else "&s=$season&e=$episode"
        val token = app.get("$superembedAPI/directstream.php?video_id=$tmdbId&tmdb=1$path").url.substringAfter("?play=")
        val (server, id) = app.post("$api/response.php", data = mapOf("token" to token), headers = mapOf("X-Requested-With" to "XMLHttpRequest")).document.select("ul.sources-list li:contains(vipstream-S)").let { it.attr("data-server") to it.attr("data-id") }
        val playUrl = "$api/playvideo.php?video_id=$id&server_id=$server&token=$token&init=1"
        val playRes = app.get(playUrl).document
        val iframe = playRes.selectFirst("iframe.source-frame")?.attr("src")
        val json = app.get(iframe ?: return).text.substringAfter("Playerjs(").substringBefore(");")
        val video = """file:"([^"]+)""".toRegex().find(json)?.groupValues?.get(1)
        callback.invoke(newExtractorLink("Superembed", "Superembed", video ?: return, INFER_TYPE) { this.headers = mapOf("Accept" to "*/*") })
        """subtitle:"([^"]+)""".toRegex().find(json)?.groupValues?.get(1)?.split(",")?.map {
            val (subLang, subUrl) = Regex("""\[(\w+)](http\S+)""").find(it)?.destructured ?: return@map
            subtitleCallback.invoke(newSubtitleFile(subLang.trim(), subUrl.trim()))
        }
    }
}
