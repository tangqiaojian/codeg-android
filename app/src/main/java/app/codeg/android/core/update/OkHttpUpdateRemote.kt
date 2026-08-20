package app.codeg.android.core.update

import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class OkHttpUpdateRemote(
    private val client: OkHttpClient = defaultClient(),
) : UpdateRemote {

    override suspend fun getText(url: String): String {
        val call = client.newCall(request(url))
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                return resp.body?.string() ?: error("empty body")
            }
        } catch (e: Exception) {
            call.cancel()
            throw e
        }
    }

    override suspend fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        val call = client.newCall(request(url))
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("empty body")
                val total = body.contentLength()
                dest.parentFile?.mkdirs()
                dest.outputStream().buffered().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(16 * 1024)
                        var received = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            received += n
                            onProgress(received, total)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            call.cancel()
            dest.delete()
            throw e
        }
    }

    private fun request(url: String): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", "Codeg-Android")
            .build()

    companion object {
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
    }
}
