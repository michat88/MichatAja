package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

object AdiDewasaHelper {
    // Header untuk bypass proteksi Dramafull
    val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/plain, */*",
        "Referer" to "https://dramafull.cc/"
    )

    // BRIDGE: Cari ID TMDB berdasarkan Judul dari Dramafull
    // Ini diperlukan agar Vidsrc/Vidlink bisa jalan
    suspend fun fetchTmdbId(title: String, year: Int?, isMovie: Boolean): Int? {
        try {
            val cleanTitle = URLEncoder.encode(title, "UTF-8")
            val type = if (isMovie) "movie" else "tv"
            // API Key Publik TMDB
            val apiKey = "b030404650f279792a8d3287232358e3" 
            val url = "https://api.themoviedb.org/3/search/$type?api_key=$apiKey&query=$cleanTitle&year=$year"
            
            val res = app.get(url).parsedSafe<TmdbSearchResponse>()
            return res?.results?.firstOrNull()?.id
        } catch (e: Exception) {
            return null
        }
    }
}

// --- Helpers Umum & Enkripsi untuk Extractor AdiDrakor ---

fun String.createSlug(): String? {
    return this.filter { it.isWhitespace() || it.isLetterOrDigit() }.trim().replace("\\s+".toRegex(), "-").lowercase()
}

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) return url
    if (url.isEmpty()) return ""
    if (url.startsWith("//")) return "https:$url"
    return if (url.startsWith('/')) domain + url else "$domain/$url"
}

fun getQualityFromName(str: String?): Int {
    return when {
        str == null -> Qualities.Unknown.value
        str.contains("2160") || str.contains("4k", true) -> Qualities.P2160.value
        str.contains("1080") -> Qualities.P1080.value
        str.contains("720") -> Qualities.P720.value
        str.contains("480") -> Qualities.P480.value
        str.contains("360") -> Qualities.P360.value
        else -> Qualities.Unknown.value
    }
}

fun String.xorDecrypt(key: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < this.length) {
        var j = 0
        while (j < key.length && i < this.length) {
            sb.append((this[i].code xor key[j].code).toChar())
            j++
            i++
        }
    }
    return sb.toString()
}

fun safeBase64Decode(input: String): String {
    var padded = input
    while (padded.length % 4 != 0) padded += "="
    return String(Base64.getDecoder().decode(padded))
}

fun base64UrlEncode(input: ByteArray): String {
    return Base64.getUrlEncoder().encodeToString(input).replace("=", "")
}

object VidrockHelper {
    private const val Ww = "x7k9mPqT2rWvY8zA5bC3nF6hJ2lK4mN9"
    fun encrypt(r: Int?, e: String, t: Int?, n: Int?): String {
        val s = if (e == "tv") "${r}_${t}_${n}" else r.toString()
        val keyBytes = Ww.toByteArray(Charsets.UTF_8)
        val ivBytes = Ww.substring(0, 16).toByteArray(Charsets.UTF_8)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(ivBytes)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        return base64UrlEncode(cipher.doFinal(s.toByteArray(Charsets.UTF_8)))
    }
}

object VidsrcHelper {
    fun encryptAesCbc(plainText: String, keyText: String): String {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(keyText.toByteArray(Charsets.UTF_8))
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val iv = ByteArray(16) { 0 }
        val ivSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        return base64UrlEncode(cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)))
    }
}
