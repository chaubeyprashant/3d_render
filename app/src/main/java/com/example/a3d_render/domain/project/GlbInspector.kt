package com.example.a3d_render.domain.project

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

data class GlbInfo(
    val version: Int,
    /** Total file length declared in the GLB header. */
    val declaredLength: Long,
    /** Relative URIs of external buffers/images the model needs (data: URIs excluded), decoded. */
    val externalUris: List<String>,
    val extensionsUsed: List<String> = emptyList()
)

/**
 * Reads only the GLB header and JSON chunk, so a corrupted or unsupported file is rejected
 * before a potentially large copy or download.
 */
object GlbInspector {
    private const val MAGIC_GLTF = 0x46546C67 // "glTF" little-endian
    private const val CHUNK_JSON = 0x4E4F534A // "JSON" little-endian
    private const val HEADER_BYTES = 12
    private const val MAX_JSON_BYTES = 64 * 1024 * 1024

    /** Fixed-size header + JSON chunk header. */
    const val PREAMBLE_BYTES = 20

    /**
     * Validates the preamble and returns how many leading bytes [inspect] reads, so remote
     * sources can fetch only that range.
     */
    fun requiredHeadBytes(input: InputStream): Long =
        PREAMBLE_BYTES + readPreamble(input).jsonLength

    fun inspect(input: InputStream): GlbInfo {
        val (version, declaredLength, jsonLength) = readPreamble(input)
        val jsonBytes = readExactly(input, jsonLength.toInt(), CORRUPTED)
        val root = runCatching {
            JsonParser.parseString(String(jsonBytes, Charsets.UTF_8)).asJsonObject
        }.getOrElse { throw ProjectImportException(CORRUPTED, it) }

        val uris = (root.uris("buffers") + root.uris("images"))
            .filterNot { it.startsWith("data:", ignoreCase = true) }
            .map(::percentDecode)
            .distinct()
        val extensions = root.get("extensionsUsed")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }.orEmpty()
        return GlbInfo(version, declaredLength, uris, extensions)
    }

    private data class Preamble(val version: Int, val declaredLength: Long, val jsonLength: Long)

    private fun readPreamble(input: InputStream): Preamble {
        val header = readExactly(input, HEADER_BYTES, "The file is too small to be a GLB model.")
        val magic = header.intLe(0)
        if (magic != MAGIC_GLTF) {
            throw ProjectImportException(
                if (header[0] == '{'.code.toByte()) {
                    "This is a .gltf (JSON) file, not a GLB. Export the model as binary GLB."
                } else {
                    "The file is not a valid GLB model. It may be corrupted or in an unsupported format."
                }
            )
        }
        val version = header.intLe(4)
        if (version != 2) {
            throw ProjectImportException(
                "Unsupported glTF version $version. Only glTF 2.0 GLB files are supported."
            )
        }
        val declaredLength = header.intLe(8).toLong() and 0xFFFFFFFFL

        val chunkHeader = readExactly(input, 8, CORRUPTED)
        val jsonLength = chunkHeader.intLe(0).toLong() and 0xFFFFFFFFL
        if (chunkHeader.intLe(4) != CHUNK_JSON || jsonLength <= 0 ||
            jsonLength > MAX_JSON_BYTES || jsonLength + PREAMBLE_BYTES > declaredLength
        ) {
            throw ProjectImportException(CORRUPTED)
        }
        return Preamble(version, declaredLength, jsonLength)
    }

    private fun JsonObject.uris(arrayName: String): List<String> {
        val array = get(arrayName)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        return array.mapNotNull { element ->
            element.takeIf { it.isJsonObject }?.asJsonObject?.get("uri")
                ?.takeIf { it.isJsonPrimitive }?.asString
        }
    }

    /** RFC 3986 percent-decoding (unlike URLDecoder, `+` stays a plus). */
    internal fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val result = StringBuilder(value.length)
        val pendingBytes = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            val hex = if (c == '%' && i + 2 < value.length) value.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (hex != null) {
                pendingBytes.write(hex)
                i += 3
                continue
            }
            if (pendingBytes.size() > 0) {
                result.append(pendingBytes.toString(Charsets.UTF_8.name()))
                pendingBytes.reset()
            }
            result.append(c)
            i++
        }
        if (pendingBytes.size() > 0) result.append(pendingBytes.toString(Charsets.UTF_8.name()))
        return result.toString()
    }

    private fun readExactly(input: InputStream, count: Int, errorMessage: String): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        try {
            while (offset < count) {
                val read = input.read(buffer, offset, count - offset)
                if (read < 0) throw EOFException()
                offset += read
            }
        } catch (e: EOFException) {
            throw ProjectImportException(errorMessage, e)
        }
        return buffer
    }

    private fun ByteArray.intLe(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)

    const val CORRUPTED = "The GLB file is corrupted or incomplete and cannot be opened."
}
