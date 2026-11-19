package com.example.a3d_render.domain.project

import java.net.URI
import java.net.URLDecoder

/** A Google Drive target parsed from a link the user pasted. */
sealed class ParsedDriveLink {
    data class File(val id: String, val resourceKey: String?) : ParsedDriveLink()
    data object Folder : ParsedDriveLink()
    data object Invalid : ParsedDriveLink()

    /** User-facing reason this link cannot be downloaded, or null when it is a file link. */
    fun rejectionMessage(): String? = when (this) {
        is File -> null
        Folder -> "That link opens a Drive folder. Paste a link to the .glb file itself."
        Invalid -> "Paste a Google Drive file link, like https://drive.google.com/file/d/…/view"
    }
}

/**
 * Reads a file id out of the share links Google Drive copies to the clipboard.
 * Folder links are recognized so they can be rejected instead of downloaded.
 */
object DriveLinkParser {
    private val fileInPath = Regex("""/file/d/([A-Za-z0-9_-]{10,})""")
    private val folderInPath = Regex("""/folders/([A-Za-z0-9_-]{10,})""")
    private val embeddedUrl = Regex("""https?://\S+""")

    fun parse(raw: String): ParsedDriveLink {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ParsedDriveLink.Invalid
        val extracted = embeddedUrl.find(trimmed)?.value?.trimEnd('.', ',', ')', ']', '"', '\'') ?: trimmed
        val withScheme = if (extracted.startsWith("http://") || extracted.startsWith("https://")) {
            extracted
        } else {
            "https://$extracted"
        }
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return ParsedDriveLink.Invalid
        val host = uri.host?.lowercase().orEmpty()
        if (host != "drive.google.com" && host != "docs.google.com" && host != "drive.usercontent.google.com") {
            return ParsedDriveLink.Invalid
        }
        val path = uri.path.orEmpty()
        val resourceKey = queryParam(uri.rawQuery, "resourcekey")
        fileInPath.find(path)?.groupValues?.get(1)?.let { id ->
            return ParsedDriveLink.File(id, resourceKey)
        }
        if (folderInPath.containsMatchIn(path)) return ParsedDriveLink.Folder
        val id = queryParam(uri.rawQuery, "id")
        if (id != null && FILE_ID.matches(id)) return ParsedDriveLink.File(id, resourceKey)
        return ParsedDriveLink.Invalid
    }

    private fun queryParam(rawQuery: String?, name: String): String? {
        if (rawQuery.isNullOrEmpty()) return null
        return rawQuery.split('&').firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=')
            if (key != name) return@firstNotNullOfOrNull null
            val value = part.substringAfter('=', "")
            runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value).ifBlank { null }
        }
    }

    private val FILE_ID = Regex("""[A-Za-z0-9_-]{10,}""")
}
