package com.example.a3d_render.data.project

import java.io.File
import java.security.MessageDigest

/**
 * On-device copies of opened projects (GLB + related files), one directory per project, so
 * recent projects reopen instantly and offline. Least-recently-opened copies are evicted
 * beyond [budgetBytes]; an evicted project is fetched again from its source when reopened.
 */
class ProjectCache(
    private val baseDir: File,
    private val budgetBytes: Long = DEFAULT_BUDGET_BYTES
) {

    fun directoryFor(projectId: String): File = File(baseDir, key(projectId))

    /** Fresh, empty directory to import into; swapped in with [commit] once complete. */
    fun newStagingDirectory(projectId: String): File =
        File(baseDir, key(projectId) + STAGING_SUFFIX).apply {
            deleteRecursively()
            mkdirs()
        }

    fun commit(projectId: String, staging: File): File {
        val target = directoryFor(projectId)
        target.deleteRecursively()
        if (!staging.renameTo(target)) {
            staging.deleteRecursively()
            throw IllegalStateException("Could not finalize project files on device storage.")
        }
        return target
    }

    fun touch(projectId: String) {
        directoryFor(projectId).setLastModified(System.currentTimeMillis())
    }

    fun delete(projectId: String) {
        directoryFor(projectId).deleteRecursively()
    }

    /** Deletes caches not in [keepProjectIds], then evicts the oldest until within budget. */
    fun prune(keepProjectIds: Collection<String>, pinnedProjectId: String?) {
        val keepKeys = keepProjectIds.map(::key).toSet()
        val dirs = baseDir.listFiles().orEmpty().filter { it.isDirectory }
        dirs.filter { it.name.endsWith(STAGING_SUFFIX) || it.name !in keepKeys }
            .forEach { it.deleteRecursively() }

        val pinnedKey = pinnedProjectId?.let(::key)
        val kept = dirs.filter { it.exists() }.sortedBy { it.lastModified() }.toMutableList()
        var total = kept.sumOf { it.walkBottomUp().filter(File::isFile).sumOf(File::length) }
        for (dir in kept) {
            if (total <= budgetBytes) break
            if (dir.name == pinnedKey) continue
            total -= dir.walkBottomUp().filter(File::isFile).sumOf(File::length)
            dir.deleteRecursively()
        }
    }

    private fun key(projectId: String): String =
        MessageDigest.getInstance("SHA-256").digest(projectId.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }

    companion object {
        private const val STAGING_SUFFIX = ".staging"
        private const val DEFAULT_BUDGET_BYTES = 1024L * 1024L * 1024L

        /** Removes single-file caches written by earlier app versions. */
        fun purgeLegacyFiles(vararg dirs: File) {
            dirs.forEach { dir ->
                dir.listFiles()?.filter {
                    it.isFile && (it.name.startsWith("glb_cache_") || it.name.startsWith("active_"))
                }?.forEach { it.delete() }
            }
        }
    }
}
