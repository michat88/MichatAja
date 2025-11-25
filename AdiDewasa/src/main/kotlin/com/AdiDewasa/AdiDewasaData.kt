package com.AdiDewasa

import com.fasterxml.jackson.annotation.JsonProperty

// --- Data Class untuk Parsing JSON API ---

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
    @JsonProperty("poster") val poster: String? = null
)

data class ApiSearchResponse(
    @JsonProperty("data") val data: List<MediaItem>? = null
)

// --- Data Class Internal untuk Menyimpan Info Link ---
// UPDATE: Menambahkan field tmdbId
data class LinkData(
    val url: String,
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val tmdbId: Int? = null 
)

// --- Helper Class untuk Extractor ---

data class ResponseHash(
    @JsonProperty("embed_url") val embed_url: String,
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("type") val type: String? = null
)

data class PrimeboxSources(
    @JsonProperty("streams") val streams: Map<String, String>? = null
)

data class TmdbSearchResponse(
    @JsonProperty("results") val results: List<TmdbResult>? = null
)

data class TmdbResult(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null
)
