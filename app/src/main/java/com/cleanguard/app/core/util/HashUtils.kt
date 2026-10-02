package com.cleanguard.app.core.util

import java.io.InputStream
import java.security.MessageDigest

/** Cálculo de SHA-256 em streaming (não carrega o arquivo inteiro na memória). */
object HashUtils {

    private const val DEFAULT_BUFFER = 64 * 1024
    private val HEX = "0123456789abcdef".toCharArray()

    /**
     * Calcula o SHA-256 de um stream. O stream NÃO é fechado aqui — use `.use { }` no chamador.
     * @param onProgress chamado com o total de bytes lidos até o momento.
     */
    fun sha256(
        input: InputStream,
        bufferSize: Int = DEFAULT_BUFFER,
        onProgress: ((bytesRead: Long) -> Unit)? = null,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(bufferSize)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            total += read
            onProgress?.invoke(total)
        }
        return toHex(digest.digest())
    }

    fun sha256(bytes: ByteArray): String =
        toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))

    fun toHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        bytes.forEachIndexed { i, b ->
            val v = b.toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(out)
    }

    /** Verifica se a string é um SHA-256 hexadecimal válido (64 caracteres). */
    fun isValidSha256(hex: String?): Boolean =
        hex != null && hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    /** Normaliza para minúsculas e remove separadores ("AB:CD" -> "abcd"). */
    fun normalize(hex: String): String = hex.replace(":", "").replace(" ", "").lowercase()

    /** Formato de impressão digital de certificado: "AB:CD:EF:...". */
    fun fingerprint(hex: String): String =
        normalize(hex).uppercase().chunked(2).joinToString(":")
}
