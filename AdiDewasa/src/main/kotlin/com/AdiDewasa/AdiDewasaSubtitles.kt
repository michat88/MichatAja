package com.AdiDewasa

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import java.util.Locale

object AdiDewasaSubtitles {

    private const val SubtitlesAPI = "https://opensubtitles-v3.strem.io"
    private const val WyZIESUBAPI = "https://sub.wyzie.ru"

    // --- OpenSubtitles v3 Logic ---
    suspend fun invokeSubtitleAPI(
        imdbId: String?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        if (imdbId.isNullOrBlank()) return

        val url = if (season == null) {
            "$SubtitlesAPI/subtitles/movie/$imdbId.json"
        } else {
            "$SubtitlesAPI/subtitles/series/$imdbId:$season:$episode.json"
        }

        try {
            val response = app.get(url)
            if (response.code != 200) return

            response.parsedSafe<SubtitlesAPIResponse>()?.subtitles?.forEach {
                val lang = getLanguage(it.lang)
                if (it.url.isNotBlank()) {
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            lang.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() },
                            it.url
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Wyzie Subs Logic ---
    suspend fun invokeWyZIESUBAPI(
        imdbId: String?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        if (imdbId.isNullOrBlank()) return

        val url = buildString {
            append("$WyZIESUBAPI/search?id=$imdbId")
            if (season != null && episode != null) append("&season=$season&episode=$episode")
        }

        try {
            val response = app.get(url)
            if (response.code != 200) return

            // Wyzie mengembalikan List/Array JSON langsung
            val subtitles = parseJson<List<WyZIESUB>>(response.text)
            
            subtitles.forEach {
                val language = it.display.replaceFirstChar { ch -> ch.titlecase(Locale.getDefault()) }
                if (it.url.isNotBlank()) {
                    subtitleCallback(newSubtitleFile(language, it.url))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Helper ---
    private fun getLanguage(code: String): String {
        val languageMap: Map<String, String> = mapOf(
            "en" to "English", "id" to "Indonesian", "ms" to "Malay",
            "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese",
            "es" to "Spanish", "fr" to "French", "de" to "German",
            "ru" to "Russian", "hi" to "Hindi", "ar" to "Arabic",
            "pt" to "Portuguese", "it" to "Italian", "th" to "Thai",
            "vi" to "Vietnamese"
        )
        return languageMap[code.lowercase()] ?: code
    }

    // --- Data Classes for Parsing ---
    data class SubtitlesAPIResponse(
        @JsonProperty("subtitles") val subtitles: List<Subtitle>? = null
    )

    data class Subtitle(
        @JsonProperty("id") val id: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("lang") val lang: String
    )

    data class WyZIESUB(
        @JsonProperty("url") val url: String,
        @JsonProperty("display") val display: String,
        @JsonProperty("language") val language: String
    )
}
