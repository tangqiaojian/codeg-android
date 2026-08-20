package app.codeg.android.core.update

import java.io.File
import java.security.MessageDigest

object Checksum {
    private val hex64 = Regex("^[0-9a-fA-F]{64}$")

    fun parseSha256File(body: String): String? {
        val token = body.trim().substringBefore(' ').substringBefore('\t').trim()
        return token.takeIf { hex64.matches(it) }?.lowercase()
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun matches(bytes: ByteArray, expectedHex: String): Boolean =
        sha256Hex(bytes).equals(expectedHex.trim(), ignoreCase = true)

    fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun matches(file: File, expectedHex: String): Boolean =
        sha256Hex(file).equals(expectedHex.trim(), ignoreCase = true)
}
