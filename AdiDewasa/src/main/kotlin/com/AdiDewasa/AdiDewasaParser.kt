package com.AdiDewasa

import com.fasterxml.jackson.annotation.JsonProperty

// Model untuk Halaman Utama (Pagination Laravel)
data class HomeResponse(
    @JsonProperty("current_page") val currentPage: Int? = null,
    @JsonProperty("data") val data: List<MediaItem>? = null,
    @JsonProperty("next_page_url") val nextPageUrl: String? = null,
    @JsonProperty("prev_page_url") val prevPageUrl: String? = null,
    @JsonProperty("total") val total: Int? = null,
    @JsonProperty("success") val success: Boolean? = null
)

// Model untuk Item Film/Series
data class MediaItem(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("image") val image: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("year") val year: String? = null
)

// Model untuk Hasil Search API
data class ApiSearchResponse(
    @JsonProperty("data") val data: List<MediaItem>? = null,
    @JsonProperty("success") val success: Boolean? = null
)

// Model LinkData untuk passing data antar fungsi load
data class LinkData(
    val url: String,
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null
)

// Model-model untuk Extractor (Disalin dari AdiDrakor agar kompatibel)
data class VixsrcSource(val name: String, val url: String, val referer: String)

data class VidFastServers(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("data") val data: String? = null
)

data class VidFastSources(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("tracks") val tracks: ArrayList<Tracks>? = null
) {
    data class Tracks(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null
    )
}

data class VidlinkSources(@JsonProperty("stream") val stream: Stream? = null) {
    data class Stream(@JsonProperty("playlist") val playlist: String? = null)
}

data class PrimeboxSources(
    @JsonProperty("streams") val streams: HashMap<String, String>? = null,
    @JsonProperty("subtitles") val subtitles: ArrayList<Subtitles>? = null
) {
    data class Subtitles(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null
    )
}

data class RageSources(@JsonProperty("url") val url: String? = null)

data class VidsrcccResponse(@JsonProperty("data") val data: ArrayList<VidsrcccServer>? = arrayListOf())

data class VidsrcccServer(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("hash") val hash: String? = null
)

data class VidsrcccResult(@JsonProperty("data") val data: VidsrcccSources? = null)

data class VidsrcccSources(
    @JsonProperty("subtitles") val subtitles: ArrayList<VidsrcccSubtitles>? = arrayListOf(),
    @JsonProperty("source") val source: String? = null
)

data class VidsrcccSubtitles(@JsonProperty("label") val label: String? = null, @JsonProperty("file") val file: String? = null)

data class UpcloudResult(@JsonProperty("sources") val sources: ArrayList<UpcloudSources>? = arrayListOf())
data class UpcloudSources(@JsonProperty("file") val file: String? = null)

data class MappleSources(@JsonProperty("data") val data: Data? = null) {
    data class Data(@JsonProperty("stream_url") val stream_url: String? = null)
}

data class MappleSubtitle(@JsonProperty("display") val display: String? = null, @JsonProperty("url") val url: String? = null)

data class WyzieSubtitle(@JsonProperty("display") val display: String? = null, @JsonProperty("url") val url: String? = null)

data class ResponseHash(
    @JsonProperty("embed_url") val embed_url: String,
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("type") val type: String? = null,
)
