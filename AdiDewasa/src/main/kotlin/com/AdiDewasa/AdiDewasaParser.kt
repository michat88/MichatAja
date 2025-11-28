package com.AdiDewasa

import com.fasterxml.jackson.annotation.JsonProperty

// --- MAIN API MODELS ---
data class HomeResponse(
    @JsonProperty("current_page") val currentPage: Int? = null,
    @JsonProperty("data") val data: List<MediaItem>? = null,
    @JsonProperty("first_page_url") val firstPageUrl: String? = null,
    @JsonProperty("from") val from: Int? = null,
    @JsonProperty("last_page") val lastPage: Int? = null,
    @JsonProperty("last_page_url") val lastPageUrl: String? = null,
    @JsonProperty("links") val links: List<Link>? = null,
    @JsonProperty("next_page_url") val nextPageUrl: String? = null,
    @JsonProperty("path") val path: String? = null,
    @JsonProperty("per_page") val perPage: Int? = null,
    @JsonProperty("prev_page_url") val prevPageUrl: String? = null,
    @JsonProperty("to") val to: Int? = null,
    @JsonProperty("total") val total: Int? = null,
    @JsonProperty("success") val success: Boolean? = null
)

data class MediaItem(
    @JsonProperty("is_adult") val isAdult: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("image") val image: String? = null,
    @JsonProperty("poster") val poster: String? = null
)

data class Link(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("active") val active: Boolean? = null
)

data class ApiSearchResponse(
    @JsonProperty("data") val data: List<MediaItem>? = null,
    @JsonProperty("success") val success: Boolean? = null
)

// --- SUBTITLE & TMDB MODELS ---
data class SubtitlesAPI(
    @JsonProperty("subtitles") val subtitles: List<Subtitle>? = null,
    @JsonProperty("cacheMaxAge") val cacheMaxAge: Long? = null,
)

data class Subtitle(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("url") val url: String,
    @JsonProperty("lang") val lang: String,
    @JsonProperty("SubEncoding") val subEncoding: String? = null,
)

data class WyZIESUB(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("url") val url: String,
    @JsonProperty("display") val display: String,
    @JsonProperty("language") val language: String? = null,
)

data class TmdbSearchResponse(
    @JsonProperty("results") val results: List<TmdbResult>? = null
)

data class TmdbResult(
    @JsonProperty("id") val id: Int? = null,
)

data class TmdbExternalIds(
    @JsonProperty("imdb_id") val imdb_id: String? = null,
)
