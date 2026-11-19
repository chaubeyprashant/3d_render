package com.example.a3d_render.domain.project

/**
 * Google Drive answers anonymous downloads of larger files with an HTML confirmation page.
 * This pulls the real download URL out of that page.
 */
internal object DriveDownloadPage {
    fun confirmUrl(html: String, fileId: String, resourceKey: String?): String? {
        val form = FORM.findAll(html).firstOrNull { match ->
            match.groupValues[1].contains("download-form", ignoreCase = true) ||
                (inputValue(match.groupValues[2], "confirm") != null && inputValue(match.groupValues[2], "id") != null)
        }
        if (form != null) {
            val action = ACTION.find(form.groupValues[1])?.groupValues?.get(1)?.replace("&amp;", "&")
                ?: "https://drive.usercontent.google.com/download"
            val body = form.groupValues[2]
            val id = inputValue(body, "id") ?: fileId
            val export = inputValue(body, "export") ?: "download"
            val confirm = inputValue(body, "confirm") ?: "t"
            val uuid = inputValue(body, "uuid")
            return buildUrl(action, id, export, confirm, uuid, resourceKey)
        }
        UC_LINK.find(html)?.let { match ->
            val confirm = match.groupValues[1]
            val id = match.groupValues[2].ifBlank { fileId }
            return buildUrl(
                "https://drive.google.com/uc",
                id,
                "download",
                confirm,
                uuid = null,
                resourceKey
            )
        }
        return null
    }

    fun failureMessage(html: String, httpCode: Int): String {
        val text = html.lowercase()
        return when {
            httpCode == 404 || "does not exist" in text || "sorry, unable to open the file" in text ->
                "This Google Drive file doesn't exist, or the link is no longer shared."
            "quota" in text || "too many users" in text || "download quota" in text ->
                "Google Drive's download limit for this file was reached. Try again later."
            httpCode == 401 || httpCode == 403 || "sign in" in text || "accounts.google.com" in text ->
                "This file isn't shared publicly. In Google Drive, set access to Anyone with the link, then paste the link again."
            else ->
                "Google Drive didn't return a GLB file. Check that the link points to a .glb shared as Anyone with the link."
        }
    }

    private fun buildUrl(
        action: String,
        id: String,
        export: String,
        confirm: String,
        uuid: String?,
        resourceKey: String?
    ): String {
        val base = when {
            action.startsWith("http://") || action.startsWith("https://") -> action.substringBefore('?')
            action.startsWith("/") -> "https://drive.google.com$action".substringBefore('?')
            else -> "https://drive.usercontent.google.com/download"
        }
        val params = linkedMapOf("id" to id, "export" to export, "confirm" to confirm)
        if (!uuid.isNullOrBlank()) params["uuid"] = uuid
        if (!resourceKey.isNullOrBlank()) params["resourcekey"] = resourceKey
        val query = params.entries.joinToString("&") { (key, value) ->
            "$key=${urlEncode(value)}"
        }
        return "$base?$query"
    }

    private fun inputValue(formBody: String, name: String): String? {
        val input = INPUT.findAll(formBody).map { it.value }.firstOrNull { tag ->
            Regex("""\bname=["']$name["']""", RegexOption.IGNORE_CASE).containsMatchIn(tag)
        } ?: return null
        return VALUE.find(input)?.groupValues?.get(1)?.replace("&amp;", "&")
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private val FORM = Regex(
        """<form\b([^>]*)>(.*?)</form>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val ACTION = Regex("""\baction=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val INPUT = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val VALUE = Regex("""\bvalue=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val UC_LINK = Regex("""confirm=([0-9A-Za-z_-]+)&(?:amp;)?id=([0-9A-Za-z_-]*)""")
}
