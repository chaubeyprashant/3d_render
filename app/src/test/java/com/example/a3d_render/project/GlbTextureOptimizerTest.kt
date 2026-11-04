package com.example.a3d_render.project

import com.example.a3d_render.domain.project.GlbInspector
import com.example.a3d_render.domain.project.GlbTextureOptimizer
import com.example.a3d_render.domain.project.ResampledImage
import com.example.a3d_render.domain.project.TextureResampler
import com.google.gson.JsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GlbTextureOptimizerTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** "Images" are `IMG` + width + height; resampling divides the dimensions. */
    private object FakeResampler : TextureResampler {
        fun encode(w: Int, h: Int, padding: Int = 64): ByteArray =
            "IMG".toByteArray() + ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(w).putInt(h).array() +
                ByteArray(padding)

        override fun size(encoded: ByteArray): Pair<Int, Int>? {
            if (encoded.size < 11 || String(encoded, 0, 3) != "IMG") return null
            val b = ByteBuffer.wrap(encoded, 3, 8).order(ByteOrder.LITTLE_ENDIAN)
            return b.int to b.int
        }

        override fun resample(encoded: ByteArray, sampleSize: Int): ResampledImage {
            val (w, h) = size(encoded)!!
            return ResampledImage(encode(w / sampleSize, h / sampleSize, padding = 8), "image/jpeg")
        }
    }

    private val geometry = ByteArray(40) { (it * 7).toByte() }

    /** GLB with two embedded textures and one geometry buffer view. */
    private fun buildGlb(file: File, vararg textureSizes: Int) {
        val bin = ByteArrayOutputStream()
        val views = mutableListOf<String>()
        fun addView(bytes: ByteArray) {
            while (bin.size() % 4 != 0) bin.write(0)
            views += """{"buffer":0,"byteOffset":${bin.size()},"byteLength":${bytes.size}}"""
            bin.write(bytes)
        }
        addView(geometry)
        textureSizes.forEach { addView(FakeResampler.encode(it, it)) }
        while (bin.size() % 4 != 0) bin.write(0)
        val images = textureSizes.indices.joinToString(",") { """{"bufferView":${it + 1},"mimeType":"image/png"}""" }
        val json = """{"asset":{"version":"2.0"},"buffers":[{"byteLength":${bin.size()}}],
            |"bufferViews":[${views.joinToString(",")}],
            |"accessors":[{"bufferView":0,"componentType":5126,"count":10,"type":"SCALAR"}],
            |"images":[$images]}""".trimMargin()
        file.writeBytes(glbBytes(json, bin.toByteArray()))
    }

    private fun glbBytes(json: String, bin: ByteArray): ByteArray {
        val jsonBytes = json.toByteArray().let { it + ByteArray((4 - it.size % 4) % 4) { ' '.code.toByte() } }
        val out = ByteArrayOutputStream()
        fun int(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        out.write("glTF".toByteArray()); int(2); int(12 + 8 + jsonBytes.size + 8 + bin.size)
        int(jsonBytes.size); out.write("JSON".toByteArray()); out.write(jsonBytes)
        int(bin.size); out.write(byteArrayOf('B'.code.toByte(), 'I'.code.toByte(), 'N'.code.toByte(), 0)); out.write(bin)
        return out.toByteArray()
    }

    private fun embeddedTextureSizes(file: File): List<Pair<Int, Int>> {
        val bytes = file.readBytes()
        val jsonLength = ByteBuffer.wrap(bytes, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val json = JsonParser.parseString(String(bytes, 20, jsonLength)).asJsonObject
        val binStart = 20 + jsonLength + 8
        val views = json.getAsJsonArray("bufferViews")
        return json.getAsJsonArray("images").map { image ->
            val view = views[image.asJsonObject["bufferView"].asInt].asJsonObject
            val offset = binStart + view["byteOffset"].asInt
            FakeResampler.size(bytes.copyOfRange(offset, offset + view["byteLength"].asInt))!!
        }
    }

    @Test
    fun oversizedTexturesAreDownscaledToFitBudget() {
        val glb = temp.newFile("model.glb")
        buildGlb(glb, 8192, 8192)
        val budget = 2 * GlbTextureOptimizer.estimateBytes(2048, 2048, 1)
        val result = GlbTextureOptimizer(FakeResampler, budget).optimize(glb, { null })

        assertEquals(2, result.texturesResized)
        assertTrue(result.estimatedBytesAfter <= budget)
        assertEquals(listOf(2048 to 2048, 2048 to 2048), embeddedTextureSizes(glb))
    }

    @Test
    fun geometryIsPreservedAndOutputIsValidGlb() {
        val glb = temp.newFile("model.glb")
        buildGlb(glb, 8192, 512)
        GlbTextureOptimizer(FakeResampler, GlbTextureOptimizer.estimateBytes(1024, 1024, 1)).optimize(glb, { null })

        val info = GlbInspector.inspect(glb.inputStream())
        assertEquals(glb.length(), info.declaredLength)

        val bytes = glb.readBytes()
        val jsonLength = ByteBuffer.wrap(bytes, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val json = JsonParser.parseString(String(bytes, 20, jsonLength)).asJsonObject
        val view = json.getAsJsonArray("bufferViews")[0].asJsonObject
        val start = 20 + jsonLength + 8 + view["byteOffset"].asInt
        assertArrayEquals(geometry, bytes.copyOfRange(start, start + geometry.size))
        assertEquals(bytes.size - (20 + jsonLength + 8), json.getAsJsonArray("buffers")[0].asJsonObject["byteLength"].asInt)
        assertEquals("image/jpeg", json.getAsJsonArray("images")[0].asJsonObject["mimeType"].asString)
    }

    @Test
    fun texturesWithinBudgetAreLeftAlone() {
        val glb = temp.newFile("model.glb")
        buildGlb(glb, 1024)
        val original = glb.readBytes()
        val result = GlbTextureOptimizer(FakeResampler, 1L shl 30).optimize(glb, { null })
        assertEquals(0, result.texturesResized)
        assertArrayEquals(original, glb.readBytes())
    }

    @Test
    fun budgetScalesWithRam() {
        val mb = 1024L * 1024L
        assertEquals(256 * mb, GlbTextureOptimizer.budgetFor(1024 * mb))
        assertEquals(500 * mb, GlbTextureOptimizer.budgetFor(4000 * mb))
        assertEquals(1024 * mb, GlbTextureOptimizer.budgetFor(16_000 * mb))
    }
}
