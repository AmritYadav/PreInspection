package com.tataaig.preinspection.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

class RotateTransformation constructor(rotationAngle: Float = 0f) : BitmapTransformation() {

    private val rotateRotationAngle = rotationAngle

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(("rotate$rotateRotationAngle").toByteArray())
    }

    override fun transform(
        pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int
    ): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(rotateRotationAngle)

        return Bitmap.createBitmap(
            toTransform, 0, 0, toTransform.width, toTransform.height, matrix, true
        )
    }


}