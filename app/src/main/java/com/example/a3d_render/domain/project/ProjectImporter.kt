package com.example.a3d_render.domain.project

import com.example.a3d_render.util.ByteFormat
import com.google.gson.JsonObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class ResolvedResource(
    /** URI as written in the GLB (decoded). */
    val uri: String,
    val node: SourceNode
)

data class ImportPlan(
    val glb: SourceNode,
    /** Path of the GLB relative to the project root, e.g. `mesh/site.glb`. */
    val glbRelativePath: String,
    val glbSizeBytes: Long,
    val resources: List<ResolvedResource>,
    val exceedsRecommendedSize: Boolean
) {
    val totalBytes: Long get() = glbSizeBytes + resources.sumOf { it.node.size.coerceAtLeast(0L) }
}

/**
 * Finds the GLB in a project folder, validates it, and resolves every external file it
 * references (textures, .bin buffers) so the whole project can be copied for offline viewing.
 */
class ProjectImporter(
    private val hardLimitBytes: Long,
    private val recommendedLimitBytes: Long = GlbSizePolicy.RECOMMENDED_MAX_BYTES
) {

    /**
     * @param glbRelativePath open this GLB instead of auto-detecting one (re-opening a project,
     * or a GLB the user tapped directly).
     */
    fun scan(tree: ProjectSourceTree, glbRelativePath: String? = null): ImportPlan {
        val listing = CachedListing(tree)
        val (glbDirs, glb) = if (glbRelativePath != null) {
            resolve(listing, listOf(tree.root), glbRelativePath)
                ?: throw ProjectImportException(
                    "The model file \"$glbRelativePath\" was not found in the project folder. " +
                        "It may have been moved, renamed or deleted."
                )
        } else {
            findGlb(listing, tree.root)
        }

        if (glb.size == 0L) {
            throw ProjectImportException("The GLB file \"${glb.name}\" is empty (0 bytes) and cannot be opened.")
        }
        checkHardLimit(glb.size)

        // Read only the header and JSON chunk so a bad file fails before any large transfer.
        val headBytes = tree.openHead(glb, GlbInspector.PREAMBLE_BYTES.toLong()).use {
            GlbInspector.requiredHeadBytes(it)
        }
        val info = tree.openHead(glb, headBytes).use { GlbInspector.inspect(it) }
        if (glb.size > 0 && info.declaredLength != glb.size) {
            throw ProjectImportException(
                "The GLB file is incomplete or corrupted (expected ${ByteFormat.format(info.declaredLength)}, " +
                    "found ${ByteFormat.format(glb.size)}). Copy or export it again."
            )
        }
        val glbSize = if (glb.size > 0) glb.size else info.declaredLength
        checkHardLimit(glbSize)

        val remote = info.externalUris.filter { it.contains("://") }
        if (remote.isNotEmpty()) {
            throw ProjectImportException(
                "The model references online resources (${remote.first()}), which are not supported. " +
                    "Export the GLB with all textures embedded or stored in the project folder."
            )
        }
        val resolved = mutableListOf<ResolvedResource>()
        val missing = mutableListOf<String>()
        info.externalUris.forEach { uri ->
            val match = resolve(listing, glbDirs, uri)
            if (match == null) missing += uri else resolved += ResolvedResource(uri, match.second)
        }
        if (missing.isNotEmpty()) {
            val shown = missing.take(3).joinToString(", ")
            val more = if (missing.size > 3) " and ${missing.size - 3} more" else ""
            throw MissingRelatedFilesException(
                missing,
                "Missing related files: $shown$more. Keep every file exported with the GLB inside the project folder."
            )
        }

        return ImportPlan(
            glb = glb,
            glbRelativePath = (glbDirs.drop(1).map { it.name } + glb.name).joinToString("/"),
            glbSizeBytes = glbSize,
            resources = resolved,
            exceedsRecommendedSize = glbSize > recommendedLimitBytes
        )
    }

    /**
     * Copies the GLB and its related files into [destination] and writes the resource manifest
     * used by the viewer. Returns the copied GLB.
     */
    fun materialize(
        tree: ProjectSourceTree,
        plan: ImportPlan,
        destination: File,
        onProgress: (Float) -> Unit,
        checkCancelled: () -> Unit = {}
    ): File {
        destination.mkdirs()
        val total = plan.totalBytes.coerceAtLeast(1L)
        var copied = 0L
        val onBytes: (Int) -> Unit = { count ->
            copied += count
            onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
            checkCancelled()
        }

        val glbFile = File(destination, safeFileName(plan.glb.name, fallback = "model.glb"))
        tree.openStream(plan.glb).use { copy(it, glbFile, onBytes) }
        if (glbFile.length() != plan.glbSizeBytes) {
            throw ProjectImportException(
                "The model could not be copied completely. Check your connection and free storage, then try again."
            )
        }

        val manifest = JsonObject()
        plan.resources.forEachIndexed { index, resource ->
            val relativePath = "$RESOURCE_DIR/${index}_${safeFileName(resource.node.name, fallback = "resource")}"
            val target = File(destination, relativePath)
            target.parentFile?.mkdirs()
            tree.openStream(resource.node).use { copy(it, target, onBytes) }
            manifest.addProperty(resource.uri, relativePath)
        }
        File(destination, RESOURCE_MANIFEST).writeText(manifest.toString())
        onProgress(1f)
        return glbFile
    }

    private fun checkHardLimit(size: Long) {
        if (size > hardLimitBytes) {
            throw ProjectImportException(
                "This model is ${ByteFormat.format(size)}, larger than the maximum this device can open " +
                    "(${ByteFormat.format(hardLimitBytes)}). Use a decimated or tiled export."
            )
        }
    }

    /** Breadth-first so the shallowest GLB wins; alphabetical within a level for determinism. */
    private fun findGlb(listing: CachedListing, root: SourceNode): Pair<List<SourceNode>, SourceNode> {
        var level = listOf(listOf(root))
        val otherModelFormats = sortedSetOf<String>()
        var directoriesVisited = 0
        for (depth in 0..MAX_SEARCH_DEPTH) {
            val glbs = mutableListOf<Pair<List<SourceNode>, SourceNode>>()
            val next = mutableListOf<List<SourceNode>>()
            for (dirs in level) {
                if (++directoriesVisited > MAX_DIRECTORIES) break
                listing.children(dirs.last()).sortedBy { it.name.lowercase() }.forEach { child ->
                    val extension = child.name.substringAfterLast('.', "").lowercase()
                    when {
                        child.isDirectory -> next += dirs + child
                        extension == "glb" -> glbs += dirs to child
                        extension in OTHER_MODEL_EXTENSIONS -> otherModelFormats += extension
                    }
                }
            }
            glbs.firstOrNull()?.let { return it }
            if (next.isEmpty()) break
            level = next
        }
        throw ProjectImportException(
            when {
                "gltf" in otherModelFormats ->
                    "This folder contains a .gltf model. Only GLB (binary glTF) files are supported — export the model as .glb."
                otherModelFormats.isNotEmpty() ->
                    "Unsupported model format (${otherModelFormats.joinToString { ".$it" }}). Only GLB files are supported."
                else -> "No GLB file found in \"${root.name}\". Select the project folder that contains the .glb model."
            }
        )
    }

    /** Resolves [relativePath] against the directory at the end of [startDirs]. */
    private fun resolve(
        listing: CachedListing,
        startDirs: List<SourceNode>,
        relativePath: String
    ): Pair<List<SourceNode>, SourceNode>? {
        val dirs = startDirs.toMutableList()
        val segments = relativePath.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        segments.forEachIndexed { index, segment ->
            val isLast = index == segments.lastIndex
            if (segment == "..") {
                if (dirs.size <= 1 || isLast) return null
                dirs.removeAt(dirs.lastIndex)
                return@forEachIndexed
            }
            val children = listing.children(dirs.last())
            val match = children.firstOrNull { it.name == segment }
                ?: children.firstOrNull { it.name.equals(segment, ignoreCase = true) }
                ?: return null
            if (isLast) return if (match.isDirectory) null else dirs.toList() to match
            if (!match.isDirectory) return null
            dirs += match
        }
        return null
    }

    private fun copy(input: InputStream, target: File, onBytes: (Int) -> Unit) {
        FileOutputStream(target).use { output ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                onBytes(read)
            }
        }
    }

    private fun safeFileName(name: String, fallback: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().ifEmpty { fallback }

    private class CachedListing(private val tree: ProjectSourceTree) {
        private val cache = HashMap<String, List<SourceNode>>()
        fun children(directory: SourceNode): List<SourceNode> =
            cache.getOrPut(directory.ref) { tree.listChildren(directory) }
    }

    companion object {
        /** Maps GLB resource URIs to copied files; read by the viewer's model loader. */
        const val RESOURCE_MANIFEST = "resources.json"
        private const val RESOURCE_DIR = "res"
        private const val MAX_SEARCH_DEPTH = 4
        private const val MAX_DIRECTORIES = 400
        private const val COPY_BUFFER_BYTES = 256 * 1024
        private val OTHER_MODEL_EXTENSIONS =
            setOf("gltf", "obj", "fbx", "ply", "stl", "las", "laz", "e57", "3ds", "dae", "usdz", "osgb")
    }
}
