package com.AdiDewasa

import com.fasterxml.jackson.annotation.JsonProperty

// --- DRAMAFULL MODELS (METADATA UTAMA) ---
data class HomeResponse(
    @JsonProperty("data") val data: List<MediaItem>? = null,
    @JsonProperty("next_page_url") val nextPageUrl: String? = null,
    @JsonProperty("success") val success: Boolean? = null
)

data class MediaItem(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("image") val image: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("year") val year: String? = null
)

data class ApiSearchResponse(
    @JsonProperty("data") val data: List<MediaItem>? = null,
    @JsonProperty("success") val success: Boolean? = null
)

// --- BRIDGE MODELS (TMDB) ---
data class TmdbSearchResponse(
    @JsonProperty("results") val results: List<TmdbResult>?
)

data class TmdbResult(
    @JsonProperty("id") val id: Int,
    @JsonProperty("media_type") val mediaType: String?
)

// --- EXTERNAL SOURCES MODELS (ADIDRAKOR) ---
data class AdimovieboxSearch(val data: AdimovieboxData?)
data class AdimovieboxData(val items: List<AdimovieboxItem>?)
data class AdimovieboxItem(val subjectId: String?, val title: String?, val releaseDate: String?, val detailPath: String?)
data class AdimovieboxStreams(val data: AdimovieboxStreamData?)
data class AdimovieboxStreamData(val streams: List<AdimovieboxStreamItem>?)
data class AdimovieboxStreamItem(val id: String?, val format: String?, val url: String?, val resolutions: String?)
data class AdimovieboxCaptions(val data: AdimovieboxCaptionData?)
data class AdimovieboxCaptionData(val captions: List<AdimovieboxCaptionItem>?)
data class AdimovieboxCaptionItem(val lanName: String?, val url: String?)

data class ResponseHash(
    @JsonProperty("embed_url") val embed_url: String,
    @JsonProperty("key") val key: String? = null
)

data class VidlinkSources(
    @JsonProperty("stream") val stream: Stream? = null,
) {
    data class Stream(@JsonProperty("playlist") val playlist: String? = null)
}

data class VidsrcccResponse(@JsonProperty("data") val data: ArrayList<VidsrcccServer>? = arrayListOf())
data class VidsrcccServer(@JsonProperty("name") val name: String?, @JsonProperty("hash") val hash: String?)
data class VidsrcccResult(@JsonProperty("data") val data: VidsrcccSources? = null)
data class VidsrcccSources(@JsonProperty("source") val source: String?, @JsonProperty("subtitles") val subtitles: ArrayList<VidsrcccSubtitles>?)
data class VidsrcccSubtitles(@JsonProperty("label") val label: String?, @JsonProperty("file") val file: String?)

data class MappleSources(@JsonProperty("data") val data: Data?) { data class Data(@JsonProperty("stream_url") val stream_url: String?) }
data class MappleSubtitle(@JsonProperty("display") val display: String?, @JsonProperty("url") val url: String?)

data class VidFastServers(@JsonProperty("name") val name: String?, @JsonProperty("data") val data: String?, @JsonProperty("description") val description: String?)
data class VidFastSources(@JsonProperty("url") val url: String?, @JsonProperty("tracks") val tracks: ArrayList<VidFastTracks>?)
data class VidFastTracks(@JsonProperty("file") val file: String?, @JsonProperty("label") val label: String?)

data class WyzieSubtitle(@JsonProperty("display") val display: String?, @JsonProperty("url") val url: String?)

data class WatchsomuchResponses(@JsonProperty("movie") val movie: WatchsomuchMovies?)
data class WatchsomuchMovies(@JsonProperty("torrents") val torrents: ArrayList<WatchsomuchTorrents>?)
data class WatchsomuchTorrents(@JsonProperty("id") val id: Int?, @JsonProperty("season") val season: Int?, @JsonProperty("episode") val episode: Int?)
data class WatchsomuchSubResponses(@JsonProperty("subtitles") val subtitles: ArrayList<WatchsomuchSubtitles>?)
data class WatchsomuchSubtitles(@JsonProperty("url") val url: String?, @JsonProperty("label") val label: String?)

data class RageSources(@JsonProperty("url") val url: String?)
data class PrimeboxSources(@JsonProperty("streams") val streams: HashMap<String, String>?, @JsonProperty("subtitles") val subtitles: ArrayList<PrimeboxSub>?)
data class PrimeboxSub(@JsonProperty("file") val file: String?, @JsonProperty("label") val label: String?)

data class GpressSources(@JsonProperty("src") val src: String, @JsonProperty("max") val max: String)
