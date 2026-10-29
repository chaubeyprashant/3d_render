package com.example.a3d_render.data.project

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.a3d_render.domain.project.ResampledImage
import com.example.a3d_render.domain.project.TextureResampler
import java.io.ByteArrayOutputStream

/** JPEG/PNG decoding with in-decoder subsampling, so an 8K texture never decodes at full size. */
class BitmapTextureResampler : TextureResampler {

    override fun size(encoded: ByteArray): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options)
        return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
    }

    override fun resample(encoded: ByteArray, sampleSize: Int): ResampledImage? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options) ?: return null
        return try {
            val out = ByteArrayOutputStream()
            if (bitmap.hasAlpha()) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                ResampledImage(out.toByteArray(), "image/png")
            } else {
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                ResampledImage(out.toByteArray(), "image/jpeg")
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val JPEG_QUALITY = 90
    }
}
