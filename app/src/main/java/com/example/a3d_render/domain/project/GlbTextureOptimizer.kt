package com.example.a3d_render.domain.project

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile

/** Decodes and re-encodes images; implemented with Android's BitmapFactory in the app. */
interface TextureResampler {
    /** Pixel size of an encoded image, or null if it cannot be read. */
    fun size(encoded: ByteArray): Pair<Int, Int>?

    /** Decodes at 1/[sampleSize] resolution (power of two) and re-encodes; null on failure. */
    fun resample(encoded: ByteArray, sampleSize: Int): ResampledImage?
}

class ResampledImage(val bytes: ByteArray, val mimeType: String)

/**
 * Downscales a GLB's textures so they fit a GPU memory budget. Photogrammetry exports often
 * embed many 8K textures: ~60 MB of JPEG decodes to several GB of RGBA + mipmaps, which gets
 * the app killed on 4–6 GB devices. Geometry and all other data are copied unchanged.
 */
class GlbTextureOptimizer(
    private val resampler: TextureResampler,
    private val budgetBytes: Long,
    private val maxSide: Int = DEFAULT_MAX_SIDE
) {
    data class Result(val texturesResized: Int, val estimatedBytesBefore: Long, val estimatedBytesAfter: Long)

    private class Texture(val imageIndex: Int, val bufferView: Int?, val externalFile: File?, val width: Int, val height: Int)

    /**
     * Rewrites [glb] in place when its textures exceed the budget. [externalImage] maps image
     * URIs to files copied alongside the GLB; those are downscaled in place too.
     */
    fun optimize(glb: File, externalImage: (String) -> File?, onProgress: (Float) -> Unit = {}): Result {
        RandomAccessFile(glb, "r").use { raf ->
            val layout = readLayout(raf)
            val root = layout.json
            val images = root.array("images")
            val views = root.array("bufferViews")
            val sharedViews = bufferViewsOutsideImages(root)

            val textures = images.mapIndexedNotNull { index, element ->
                val image = element.asJsonObject
                val viewIndex = image.get("bufferView")?.asInt
                val bytes: ByteArray
                var external: File? = null
                if (viewIndex != null) {
                    val view = views[viewIndex].asJsonObject
                    if (view.intOr("buffer", 0) != 0 || viewIndex in sharedViews) return@mapIndexedNotNull null
                    bytes = readBin(raf, layout, view.longOr("byteOffset", 0), view.get("byteLength").asLong)
                } else {
                    val uri = image.get("uri")?.asString ?: return@mapIndexedNotNull null
                    if (uri.startsWith("data:", ignoreCase = true)) return@mapIndexedNotNull null
                    external = externalImage(GlbInspector.percentDecode(uri))?.takeIf { it.isFile }
                        ?: return@mapIndexedNotNull null
                    bytes = external.readBytes()
                }
                val (w, h) = resampler.size(bytes) ?: return@mapIndexedNotNull null
                Texture(index, viewIndex, external, w, h)
            }

            val before = textures.sumOf { estimateBytes(it.width, it.height, 1) }
            val samples = chooseSampleSizes(textures)
            val after = textures.sumOf { estimateBytes(it.width, it.height, samples.getValue(it.imageIndex)) }
            val toResize = textures.filter { samples.getValue(it.imageIndex) > 1 }
            if (toResize.isEmpty()) {
                onProgress(1f)
                return Result(0, before, before)
            }

            val replacements = HashMap<Int, ResampledImage>()
            toResize.forEachIndexed { done, texture ->
                val sample = samples.getValue(texture.imageIndex)
                if (texture.externalFile != null) {
                    resampler.resample(texture.externalFile.readBytes(), sample)?.let { resized ->
                        val temp = File(texture.externalFile.path + ".tmp")
                        temp.writeBytes(resized.bytes)
                        if (!temp.renameTo(texture.externalFile)) temp.delete()
                    }
                } else {
                    val view = views[texture.bufferView!!].asJsonObject
                    val original = readBin(raf, layout, view.longOr("byteOffset", 0), view.get("byteLength").asLong)
                    resampler.resample(original, sample)?.let { resized ->
                        replacements[texture.bufferView] = resized
                        images[texture.imageIndex].asJsonObject.addProperty("mimeType", resized.mimeType)
                    }
                }
                onProgress((done + 1).toFloat() / toResize.size)
            }

            if (replacements.isNotEmpty()) {
                val temp = File(glb.path + ".tmp")
                FileOutputStream(temp).use { out -> writeGlb(raf, layout, replacements, out) }
                if (!temp.renameTo(glb)) {
                    temp.delete()
                    throw IllegalStateException("Could not save the optimized model.")
                }
            }
            return Result(toResize.size, before, after)
        }
    }

    /** Picks the largest per-texture cap (power-of-two downsampling) that fits the budget. */
    private fun chooseSampleSizes(textures: List<Texture>): Map<Int, Int> {
        var cap = maxSide
        while (true) {
            val samples = textures.associate { it.imageIndex to sampleFor(it, cap) }
            val total = textures.sumOf { estimateBytes(it.width, it.height, samples.getValue(it.imageIndex)) }
            if (total <= budgetBytes || cap <= MIN_SIDE) return samples
            cap /= 2
        }
    }

    private fun sampleFor(texture: Texture, cap: Int): Int {
        var sample = 1
        while (maxOf(texture.width, texture.height) / sample > cap) sample *= 2
        return sample
    }

    private class Layout(val json: JsonObject, val binDataStart: Long, val binLength: Long)

    private fun readLayout(raf: RandomAccessFile): Layout {
        val header = ByteArray(20).also { raf.readFully(it) }
        if (header.intLe(0) != MAGIC_GLTF) throw ProjectImportException(GlbInspector.CORRUPTED)
        val jsonLength = header.intLe(12).toLong() and 0xFFFFFFFFL
        val jsonBytes = ByteArray(jsonLength.toInt()).also { raf.readFully(it) }
        val json = JsonParser.parseString(String(jsonBytes, Charsets.UTF_8)).asJsonObject
        val binHeaderAt = 20 + jsonLength
        if (raf.length() < binHeaderAt + 8) return Layout(json, binHeaderAt + 8, 0)
        raf.seek(binHeaderAt)
        val binHeader = ByteArray(8).also { raf.readFully(it) }
        return Layout(json, binHeaderAt + 8, binHeader.intLe(0).toLong() and 0xFFFFFFFFL)
    }

    private fun readBin(raf: RandomAccessFile, layout: Layout, offset: Long, length: Long): ByteArray {
        raf.seek(layout.binDataStart + offset)
        return ByteArray(length.toInt()).also { raf.readFully(it) }
    }

    /** Streams a new GLB: images swapped for [replacements], other buffer views copied verbatim. */
    private fun writeGlb(raf: RandomAccessFile, layout: Layout, replacements: Map<Int, ResampledImage>, out: OutputStream) {
        val root = layout.json
        val views = root.array("bufferViews")
        val order = views.indices
            .filter { views[it].asJsonObject.intOr("buffer", 0) == 0 }
            .sortedBy { views[it].asJsonObject.longOr("byteOffset", 0) }
        val originalOffsets = order.associateWith { views[it].asJsonObject.longOr("byteOffset", 0) }
        val originalLengths = order.associateWith { views[it].asJsonObject.get("byteLength").asLong }

        var offset = 0L
        order.forEach { index ->
            offset = align4(offset)
            val view = views[index].asJsonObject
            val length = replacements[index]?.bytes?.size?.toLong() ?: originalLengths.getValue(index)
            view.addProperty("byteOffset", offset)
            view.addProperty("byteLength", length)
            offset += length
        }
        val binLength = align4(offset)
        root.array("buffers").firstOrNull()?.asJsonObject?.addProperty("byteLength", binLength)

        val jsonBytes = Gson().toJson(root).toByteArray(Charsets.UTF_8).let { raw ->
            raw + ByteArray(((4 - raw.size % 4) % 4)) { ' '.code.toByte() }
        }
        out.write(intsLe(MAGIC_GLTF, 2, (12 + 8 + jsonBytes.size + 8 + binLength).toInt()))
        out.write(intsLe(jsonBytes.size, CHUNK_JSON))
        out.write(jsonBytes)
        out.write(intsLe(binLength.toInt(), CHUNK_BIN))

        var written = 0L
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        order.forEach { index ->
            val target = views[index].asJsonObject.get("byteOffset").asLong
            while (written < target) { out.write(0); written++ }
            val replacement = replacements[index]
            if (replacement != null) {
                out.write(replacement.bytes)
                written += replacement.bytes.size
            } else {
                var remaining = originalLengths.getValue(index)
                raf.seek(layout.binDataStart + originalOffsets.getValue(index))
                while (remaining > 0) {
                    val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) throw ProjectImportException(GlbInspector.CORRUPTED)
                    out.write(buffer, 0, read)
                    remaining -= read
                    written += read
                }
            }
        }
        while (written < binLength) { out.write(0); written++ }
    }

    /** Buffer views referenced anywhere except `images` (accessors, Draco, …) must stay intact. */
    private fun bufferViewsOutsideImages(root: JsonObject): Set<Int> {
        val result = HashSet<Int>()
        fun visit(element: JsonElement) {
            when {
                element.isJsonObject -> element.asJsonObject.entrySet().forEach { (key, value) ->
                    if (key == "bufferView" && value.isJsonPrimitive) result += value.asInt else visit(value)
                }
                element.isJsonArray -> element.asJsonArray.forEach(::visit)
            }
        }
        root.entrySet().filter { it.key != "images" }.forEach { visit(it.value) }
        return result
    }

    private fun JsonObject.array(name: String): JsonArray = get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
    private fun JsonObject.intOr(name: String, default: Int) = get(name)?.asInt ?: default
    private fun JsonObject.longOr(name: String, default: Long) = get(name)?.asLong ?: default
    private val JsonArray.indices get() = 0 until size()

    private fun align4(value: Long) = (value + 3) and 3L.inv()

    private fun ByteArray.intLe(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or ((this[offset + 3].toInt() and 0xFF) shl 24)

    private fun intsLe(vararg values: Int): ByteArray = ByteArray(values.size * 4).also { bytes ->
        values.forEachIndexed { i, v -> for (b in 0..3) bytes[i * 4 + b] = (v ushr (8 * b)).toByte() }
    }

    companion object {
        const val DEFAULT_MAX_SIDE = 4096
        private const val MIN_SIDE = 256
        private const val MAGIC_GLTF = 0x46546C67
        private const val CHUNK_JSON = 0x4E4F534A
        private const val CHUNK_BIN = 0x004E4942
        private const val COPY_BUFFER_BYTES = 256 * 1024

        /** RGBA8 plus a full mip chain (×4/3). */
        fun estimateBytes(width: Int, height: Int, sampleSize: Int): Long {
            val w = maxOf(1, width / sampleSize).toLong()
            val h = maxOf(1, height / sampleSize).toLong()
            return w * h * 4 * 4 / 3
        }

        /** About 1/8 of RAM for textures (~460 MB on a 4 GB device). */
        fun budgetFor(totalRamBytes: Long): Long =
            (totalRamBytes / 8).coerceIn(256L * 1024 * 1024, 1024L * 1024 * 1024)
    }
}
