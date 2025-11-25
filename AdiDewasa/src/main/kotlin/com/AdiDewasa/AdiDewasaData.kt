package com.AdiDewasa

import com.fasterxml.jackson.annotation.JsonProperty

// Data Class untuk parsing Home Page API
data class HomeResponse(
    @JsonProperty("data") val data: List<MediaItem>? = null,
    @JsonProperty("next_page_url") val nextPageUrl: String? = null
)

data class MediaItem(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("image") val image: String? = null,
    @JsonProperty("poster") val poster: String? = null
)

// Data Class untuk Search API
data class ApiSearchResponse(
    @JsonProperty("data") val data: List<MediaItem>? = null
)

// Data Class untuk LinkData (disimpan di load() dan dipakai di loadLinks())
data class LinkData(
    val url: String,
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null
)

// Helper Class untuk Extractor WpMovies
data class ResponseHash(
    @JsonProperty("embed_url") val embed_url: String,
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("type") val type: String? = null
)

data class PrimeboxSources(
    @JsonProperty("streams") val streams: Map<String, String>? = null
)
