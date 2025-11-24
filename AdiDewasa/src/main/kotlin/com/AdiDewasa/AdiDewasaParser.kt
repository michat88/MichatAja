package com.AdiDewasa

import com.fasterxml.jackson.annotation.JsonProperty

// --- MODEL DATA UNTUK DRAMAFULL API ---

data class HomeResponse(
    @JsonProperty("current_page") val currentPage: Int? = null,
    @JsonProperty("data") val data: List<MediaItem>? = null,
    @JsonProperty("next_page_url") val nextPageUrl: String? = null,
    @JsonProperty("success") val success: Boolean? = null
)

data class MediaItem(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("image") val image: String? = null,
    @JsonProperty("poster") val poster: String? = null
)

data class ApiSearchResponse(
    @JsonProperty("data") val data: List<MediaItem>? = null,
    @JsonProperty("success") val success: Boolean? = null
)

// --- MODEL DATA UNTUK HYBRID SYSTEM (INTERNAL) ---

// Data yang dikirim dari Provider ke Extractor
data class AdiLinkInfo(
    val url: String,
    val title: String,
    val year: Int?,
    val episode: Int? = null,
    val season: Int? = null
)

// --- MODEL DATA UNTUK TMDB & WYZIE SEARCH ---

data class TmdbSearch(
    @JsonProperty("results") val results: List<TmdbRes>? = null
)

data class TmdbRes(
    @JsonProperty("id") val id: Int? = null
)

data class TmdbExternalIds(
    @JsonProperty("imdb_id") val imdb_id: String? = null
)
