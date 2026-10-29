package com.example.a3d_render.domain.project

/** Import/validation failure whose [message] is safe to show to the user as-is. */
open class ProjectImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The GLB references external files (textures, buffers) that are not in the source. */
class MissingRelatedFilesException(val missing: List<String>, message: String) : ProjectImportException(message)
