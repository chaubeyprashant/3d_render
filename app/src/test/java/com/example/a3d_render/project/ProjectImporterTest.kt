package com.example.a3d_render.project

import com.example.a3d_render.domain.project.GlbSizePolicy
import com.example.a3d_render.domain.project.ProjectImportException
import com.example.a3d_render.domain.project.ProjectImporter
import com.example.a3d_render.domain.project.ProjectSourceTree
import com.example.a3d_render.domain.project.SourceNode
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream

class ProjectImporterTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** In-memory folder tree: paths map to file contents; folders are implied by paths. */
    private class FakeTree(private val files: Map<String, ByteArray>) : ProjectSourceTree {
        override val root = SourceNode("Survey Site", "", isDirectory = true, size = -1)

        override fun listChildren(directory: SourceNode): List<SourceNode> {
            val prefix = if (directory.ref.isEmpty()) "" else directory.ref + "/"
            return files.keys.filter { it.startsWith(prefix) }
                .map { it.removePrefix(prefix).substringBefore('/') }
                .distinct()
                .map { name ->
                    val path = prefix + name
                    val content = files[path]
                    SourceNode(name, path, isDirectory = content == null, size = content?.size?.toLong() ?: -1L)
                }
        }

        override fun openStream(file: SourceNode): InputStream = files.getValue(file.ref).inputStream()
    }

    private val importer = ProjectImporter(hardLimitBytes = 10_000, recommendedLimitBytes = 1_000)

    private fun errorOf(block: () -> Unit): String {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("Expected ProjectImportException but was $error", error is ProjectImportException)
        return error!!.message!!
    }

    @Test
    fun detectsShallowestGlbAndItsRelatedFiles() {
        val tree = FakeTree(
            mapOf(
                "notes.txt" to "x".toByteArray(),
                "mesh/site.glb" to GlbTestData.glb(GlbTestData.jsonWithUris("tex/a.jpg", "../shared/b.png")),
                "mesh/tex/a.jpg" to ByteArray(10),
                "shared/b.png" to ByteArray(20),
                "mesh/lod/deeper/other.glb" to GlbTestData.glb(GlbTestData.EMBEDDED_JSON)
            )
        )
        val plan = importer.scan(tree)
        assertEquals("mesh/site.glb", plan.glbRelativePath)
        assertEquals(listOf("tex/a.jpg", "../shared/b.png"), plan.resources.map { it.uri })
        assertFalse(plan.exceedsRecommendedSize)
    }

    @Test
    fun materializeCopiesFilesAndWritesManifest() {
        val glb = GlbTestData.glb(GlbTestData.jsonWithUris("tex/a.jpg"))
        val tree = FakeTree(mapOf("site.glb" to glb, "tex/a.jpg" to ByteArray(10) { 7 }))
        val plan = importer.scan(tree)
        val destination = temp.newFolder("project")
        var lastProgress = 0f
        val copied = importer.materialize(tree, plan, destination, onProgress = { lastProgress = it })

        assertEquals(glb.size.toLong(), copied.length())
        assertEquals(1f, lastProgress)
        val manifest = JsonParser.parseString(File(destination, ProjectImporter.RESOURCE_MANIFEST).readText()).asJsonObject
        val resource = File(destination, manifest["tex/a.jpg"].asString)
        assertTrue(resource.readBytes().all { it == 7.toByte() })
    }

    @Test
    fun forcedGlbPathIsUsed() {
        val tree = FakeTree(
            mapOf(
                "a.glb" to GlbTestData.glb(GlbTestData.EMBEDDED_JSON),
                "b.glb" to GlbTestData.glb(GlbTestData.EMBEDDED_JSON)
            )
        )
        assertEquals("b.glb", importer.scan(tree, "b.glb").glbRelativePath)
        assertTrue(errorOf { importer.scan(tree, "moved/c.glb") }.contains("was not found"))
    }

    @Test
    fun forcedFileIsValidatedByHeaderNotExtension() {
        val tree = FakeTree(mapOf("medium_model_geo.glb (1)" to GlbTestData.glb(GlbTestData.EMBEDDED_JSON)))
        assertEquals("medium_model_geo.glb (1)", importer.scan(tree, "medium_model_geo.glb (1)").glbRelativePath)

        val notGlb = FakeTree(mapOf("report.pdf" to "%PDF-1.4 not a model".toByteArray()))
        assertTrue(errorOf { importer.scan(notGlb, "report.pdf") }.contains("not a valid GLB"))
    }

    @Test
    fun missingRelatedFilesAreListed() {
        val tree = FakeTree(mapOf("site.glb" to GlbTestData.glb(GlbTestData.jsonWithUris("tex/a.jpg", "tex/b.jpg"))))
        assertEquals(
            "Missing related files: tex/a.jpg, tex/b.jpg. Keep every file exported with the GLB inside the project folder.",
            errorOf { importer.scan(tree) }
        )
    }

    @Test
    fun explainsMissingGlb() {
        assertTrue(errorOf { importer.scan(FakeTree(mapOf("readme.txt" to ByteArray(1)))) }.startsWith("No GLB file found"))
        assertTrue(errorOf { importer.scan(FakeTree(mapOf("m/model.gltf" to ByteArray(1)))) }.contains(".gltf model"))
        assertTrue(errorOf { importer.scan(FakeTree(mapOf("scan.obj" to ByteArray(1)))) }.contains("Unsupported model format (.obj)"))
    }

    @Test
    fun rejectsEmptyTruncatedAndOversizedFiles() {
        assertTrue(errorOf { importer.scan(FakeTree(mapOf("a.glb" to ByteArray(0)))) }.contains("empty"))

        val truncated = GlbTestData.glb(GlbTestData.EMBEDDED_JSON, declaredLengthDelta = 64)
        assertTrue(errorOf { importer.scan(FakeTree(mapOf("a.glb" to truncated))) }.contains("incomplete or corrupted"))

        val oversized = GlbTestData.glb(GlbTestData.EMBEDDED_JSON, binBytes = 12_000)
        assertTrue(errorOf { importer.scan(FakeTree(mapOf("a.glb" to oversized))) }.contains("larger than the maximum"))
    }

    @Test
    fun flagsFilesAboveRecommendedSize() {
        val large = GlbTestData.glb(GlbTestData.EMBEDDED_JSON, binBytes = 2_000)
        assertTrue(importer.scan(FakeTree(mapOf("a.glb" to large))).exceedsRecommendedSize)
    }

    @Test
    fun rejectsRemoteResources() {
        val tree = FakeTree(mapOf("a.glb" to GlbTestData.glb(GlbTestData.jsonWithUris("https://cdn.example.com/t.jpg"))))
        assertTrue(errorOf { importer.scan(tree) }.contains("online resources"))
    }

    @Test
    fun hardLimitScalesWithRam() {
        val mb = 1024L * 1024L
        assertEquals(300 * mb, GlbSizePolicy.hardLimitBytes(2048 * mb))
        assertEquals(400 * mb, GlbSizePolicy.hardLimitBytes(4000 * mb))
        assertEquals(1536 * mb, GlbSizePolicy.hardLimitBytes(32_000 * mb))
        assertTrue(GlbSizePolicy.hardLimitBytes(3600 * mb) > GlbSizePolicy.RECOMMENDED_MAX_BYTES)
    }
}
