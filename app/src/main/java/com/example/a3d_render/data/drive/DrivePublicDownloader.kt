package com.example.a3d_render.data.drive

import com.example.a3d_render.domain.project.DriveDownloadPage
import com.example.a3d_render.domain.project.ProjectImportException
import com.example.a3d_render.util.ByteFormat
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads one publicly shared Drive file ("Anyone with the link") without Google sign-in.
 * Calls block and write straight to [destination].
 */
class DrivePublicDownloader(
    private val client: OkHttpClient = defaultClient()
) {
    /**
     * @return the file name advertised by Drive, sanitized to a single path segment.
     * @param onProgress fraction of the body written, or null when Drive did not send a length.
     */
    fun download(
        fileId: String,
        resourceKey: String?,
        destination: File,
        maxBytes: Long,
        onProgress: (Float?) -> Unit
    ): String {
        destination.parentFile?.mkdirs()
        if (destination.exists()) destination.delete()
        val response = openFile(fileId, resourceKey)
        response.use { bodyResponse ->
            if (bodyResponse.request.url.host.contains("accounts.google.com")) {
                throw ProjectImportException(DriveDownloadPage.failureMessage("", 401))
            }
            val body = bodyResponse.body ?: throw ProjectImportException(UNAVAILABLE)
            val total = body.contentLength()
            if (total > maxBytes) {
                throw ProjectImportException(tooLarge(total, maxBytes))
            }
            val name = fileName(bodyResponse)
            try {
                FileOutputStream(destination).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var read = 0L
                        var sinceReport = 0L
                        if (total > 0L) onProgress(0f)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            read += count
                            if (read > maxBytes) throw ProjectImportException(tooLarge(read, maxBytes))
                            sinceReport += count
                            if (total > 0L && (sinceReport >= 256 * 1024 || read == total)) {
                                sinceReport = 0L
                                onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                        output.flush()
                    }
                }
            } catch (e: ProjectImportException) {
                destination.delete()
                throw e
            } catch (e: IOException) {
                destination.delete()
                throw ProjectImportException(
                    "Couldn't finish downloading from Google Drive. Check your connection and try again.",
                    e
                )
            }
            if (destination.length() == 0L) {
                destination.delete()
                throw ProjectImportException("Google Drive returned an empty file.")
            }
            if (looksLikeHtmlFile(destination)) {
                destination.delete()
                throw ProjectImportException(
                    "Google Drive didn't return a GLB file. Share the file as Anyone with the link and paste the file link, not a folder."
                )
            }
            return name
        }
    }

    private fun openFile(fileId: String, resourceKey: String?): Response {
        val firstUrl = downloadUrl(
            "https://drive.usercontent.google.com/download",
            fileId,
            resourceKey,
            confirm = "t",
            uuid = null
        )
        val first = execute(firstUrl)
        if (!looksLikeHtml(first)) return requireFile(first)
        val html = first.body?.string().orEmpty()
        val code = first.code
        first.close()
        val confirmed = DriveDownloadPage.confirmUrl(html, fileId, resourceKey)
            ?: cookieConfirmUrl(fileId, resourceKey)
        if (confirmed == null) throw ProjectImportException(DriveDownloadPage.failureMessage(html, code))
        val second = execute(confirmed)
        if (!looksLikeHtml(second)) return requireFile(second)
        val secondHtml = second.body?.string().orEmpty()
        val secondCode = second.code
        second.close()
        throw ProjectImportException(DriveDownloadPage.failureMessage(secondHtml.ifBlank { html }, secondCode))
    }

    private fun execute(url: String): Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .build()
        try {
            return client.newCall(request).execute()
        } catch (e: IOException) {
            throw ProjectImportException(
                "Couldn't reach Google Drive. Check your internet connection and try again.",
                e
            )
        }
    }

    private fun cookieConfirmUrl(fileId: String, resourceKey: String?): String? {
        val jar = client.cookieJar as? MemoryCookieJar ?: return null
        val token = jar.cookies.firstOrNull { cookie ->
            cookie.name.startsWith("download_warning")
        }?.value?.takeIf { it.isNotBlank() } ?: return null
        return downloadUrl("https://drive.google.com/uc", fileId, resourceKey, confirm = token, uuid = null)
    }

    private fun requireFile(response: Response): Response {
        if (response.isSuccessful) return response
        val code = response.code
        response.close()
        throw ProjectImportException(DriveDownloadPage.failureMessage("", code))
    }

    private fun looksLikeHtml(response: Response): Boolean {
        val type = response.body?.contentType()?.toString()?.lowercase().orEmpty()
        if ("text/html" in type || "application/xhtml" in type) return true
        val prefix = runCatching { response.peekBody(64).string().trimStart() }.getOrDefault("")
        return prefix.startsWith("<!") || prefix.startsWith("<html", ignoreCase = true)
    }

    private fun looksLikeHtmlFile(file: File): Boolean {
        val prefix = file.inputStream().use { input ->
            val bytes = ByteArray(32)
            val count = input.read(bytes)
            if (count <= 0) "" else String(bytes, 0, count, Charsets.ISO_8859_1).trimStart()
        }
        return prefix.startsWith("<!") || prefix.startsWith("<html", ignoreCase = true)
    }

    private fun fileName(response: Response): String {
        val header = response.header("Content-Disposition").orEmpty()
        val encoded = FILENAME_STAR.find(header)?.groupValues?.get(1)
        val plain = FILENAME.find(header)?.groupValues?.get(1)
        val raw = encoded ?: plain ?: "model.glb"
        val decoded = runCatching {
            java.net.URLDecoder.decode(raw, Charsets.UTF_8.name())
        }.getOrDefault(raw)
        val segment = decoded.substringAfterLast('/').substringAfterLast('\\').trim().trim('"')
        val cleaned = segment.replace(Regex("""[^\w.\- ()\[\]]"""), "_").take(120)
        return cleaned.ifBlank { "model.glb" }
    }

    private fun tooLarge(size: Long, maxBytes: Long) =
        "This model is ${ByteFormat.format(size)}, larger than the maximum this device can open " +
            "(${ByteFormat.format(maxBytes)}). Use a decimated or tiled export."

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val UNAVAILABLE =
            "Couldn't download that Google Drive file. Check the link and try again."
        private val FILENAME_STAR = Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
        private val FILENAME = Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE)

        fun downloadUrl(base: String, fileId: String, resourceKey: String?, confirm: String?, uuid: String?): String {
            val parsed = base.toHttpUrlOrNull()
                ?: throw ProjectImportException("Couldn't build the Google Drive download link.")
            val builder = parsed.newBuilder().query(null)
            builder.addQueryParameter("id", fileId)
            builder.addQueryParameter("export", "download")
            if (!confirm.isNullOrBlank()) builder.addQueryParameter("confirm", confirm)
            if (!uuid.isNullOrBlank()) builder.addQueryParameter("uuid", uuid)
            if (!resourceKey.isNullOrBlank()) builder.addQueryParameter("resourcekey", resourceKey)
            return builder.build().toString()
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .cookieJar(MemoryCookieJar())
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private class MemoryCookieJar : CookieJar {
        val cookies = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { cookie ->
                this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain }
                this.cookies += cookie
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.matches(url) }
    }
}
