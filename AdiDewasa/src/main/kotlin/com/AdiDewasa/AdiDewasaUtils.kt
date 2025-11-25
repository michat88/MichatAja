package com.AdiDewasa

import android.util.Base64
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AdiDewasaHelper {
    // Header statis agar terlihat seperti browser asli
    val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/plain, */*",
        "Connection" to "keep-alive",
        "Referer" to "https://dramafull.cc/"
    )

    fun normalizeQuery(title: String): String {
        return title
            .replace(Regex("\\(\\d{4}\\)"), "")
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .trim()
            .replace("\\s+".toRegex(), " ")
    }
}

fun base64Decode(input: String): String {
    return String(Base64.decode(input, Base64.DEFAULT))
}

fun base64Encode(input: ByteArray): String {
    return Base64.encodeToString(input, Base64.NO_WRAP)
}

fun base64UrlEncode(input: ByteArray): String {
    return base64Encode(input)
        .replace("+", "-")
        .replace("/", "_")
        .replace("=", "")
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

fun generateWpKey(r: String, m: String): String {
    val rList = r.split("\\x").toTypedArray()
    var n = ""
    val decodedM = safeBase64Decode(m.reversed())
    for (s in decodedM.split("|")) {
        n += "\\x" + rList[Integer.parseInt(s) + 1]
    }
    return n
}

fun safeBase64Decode(input: String): String {
    var paddedInput = input
    val remainder = input.length % 4
    if (remainder != 0) {
        paddedInput += "=".repeat(4 - remainder)
    }
    return base64Decode(paddedInput)
}

fun decode(input: String): String = URLDecoder.decode(input, "utf-8")
fun encode(input: String): String = URLEncoder.encode(input, "utf-8").replace("+", "%20")

fun String.fixUrlBloat(): String {
    return this.replace("\"", "").replace("\\", "")
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
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return base64UrlEncode(encrypted)
    }
}
