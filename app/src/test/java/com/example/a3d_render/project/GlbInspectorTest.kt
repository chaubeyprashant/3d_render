package com.example.a3d_render.project

import com.example.a3d_render.domain.project.GlbInspector
import com.example.a3d_render.domain.project.ProjectImportException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlbInspectorTest {

    @Test
    fun readsHeaderAndExternalUris() {
        val bytes = GlbTestData.glb(GlbTestData.jsonWithUris("textures/tile%200.jpg", "roof.png"))
        val info = GlbInspector.inspect(bytes.inputStream())
        assertEquals(2, info.version)
        assertEquals(bytes.size.toLong(), info.declaredLength)
        assertEquals(listOf("textures/tile 0.jpg", "roof.png"), info.externalUris)
    }

    @Test
    fun requiredHeadBytesCoversPreambleAndJsonOnly() {
        val bytes = GlbTestData.glb(GlbTestData.EMBEDDED_JSON, binBytes = 4096)
        val head = GlbInspector.requiredHeadBytes(bytes.inputStream())
        assertTrue(head < bytes.size - 4096)
        // The head alone is enough to inspect the file.
        val info = GlbInspector.inspect(bytes.copyOf(head.toInt()).inputStream())
        assertEquals(bytes.size.toLong(), info.declaredLength)
    }

    @Test
    fun embeddedModelHasNoExternalUris() {
        val info = GlbInspector.inspect(GlbTestData.glb(GlbTestData.EMBEDDED_JSON).inputStream())
        assertTrue(info.externalUris.isEmpty())
    }

    @Test
    fun rejectsNonGlb() {
        val error = runCatching { GlbInspector.inspect("PK\u0003\u0004 not a model".byteInputStream()) }.exceptionOrNull()
        assertTrue(error is ProjectImportException)
        assertTrue(error!!.message!!.contains("not a valid GLB"))
    }

    @Test
    fun explainsGltfJson() {
        val error = runCatching { GlbInspector.inspect("{\"asset\":{}}   ".byteInputStream()) }.exceptionOrNull()
        assertTrue(error!!.message!!.contains(".gltf"))
    }

    @Test
    fun rejectsGltfVersion1() {
        val error = runCatching {
            GlbInspector.inspect(GlbTestData.glb(GlbTestData.EMBEDDED_JSON, version = 1).inputStream())
        }.exceptionOrNull()
        assertTrue(error!!.message!!.contains("Unsupported glTF version 1"))
    }

    @Test
    fun rejectsTruncatedJsonChunk() {
        val bytes = GlbTestData.glb(GlbTestData.EMBEDDED_JSON).copyOf(30)
        val error = runCatching { GlbInspector.inspect(bytes.inputStream()) }.exceptionOrNull()
        assertEquals(GlbInspector.CORRUPTED, error!!.message)
    }

    @Test
    fun percentDecodingKeepsPlusAndHandlesUtf8() {
        assertEquals("a+b c", GlbInspector.percentDecode("a+b%20c"))
        assertEquals("café.png", GlbInspector.percentDecode("caf%C3%A9.png"))
        assertEquals("100%", GlbInspector.percentDecode("100%"))
    }
}
