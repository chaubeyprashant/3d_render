package com.example.a3d_render.project

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds minimal GLB byte streams for tests. */
object GlbTestData {
    fun glb(json: String, binBytes: Int = 16, version: Int = 2, declaredLengthDelta: Int = 0): ByteArray {
        val jsonBytes = json.toByteArray().let { raw ->
            raw + ByteArray((4 - raw.size % 4) % 4) { ' '.code.toByte() }
        }
        val total = 12 + 8 + jsonBytes.size + 8 + binBytes
        val out = ByteArrayOutputStream()
        fun int(value: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
        out.write("glTF".toByteArray())
        int(version)
        int(total + declaredLengthDelta)
        int(jsonBytes.size)
        out.write("JSON".toByteArray())
        out.write(jsonBytes)
        int(binBytes)
        out.write(byteArrayOf('B'.code.toByte(), 'I'.code.toByte(), 'N'.code.toByte(), 0))
        out.write(ByteArray(binBytes))
        return out.toByteArray()
    }

    const val EMBEDDED_JSON = """{"asset":{"version":"2.0"},"buffers":[{"byteLength":16}]}"""

    fun jsonWithUris(vararg uris: String): String {
        val images = uris.joinToString(",") { "{\"uri\":\"$it\"}" }
        return """{"asset":{"version":"2.0"},"buffers":[{"byteLength":16},{"uri":"data:application/octet-stream;base64,AAAA"}],"images":[$images]}"""
    }
}
