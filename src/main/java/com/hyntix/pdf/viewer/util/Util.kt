package com.hyntix.pdf.viewer.util

import android.content.Context
import android.util.TypedValue
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

object Util {
    private const val DEFAULT_BUFFER_SIZE = 1024 * 4

    fun getDP(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    @Throws(IOException::class)
    fun toByteArray(inputStream: InputStream): ByteArray {
        val os = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var n: Int
        while (inputStream.read(buffer).also { n = it } != -1) {
            os.write(buffer, 0, n)
        }
        return os.toByteArray()
    }

    @Throws(IOException::class)
    fun getSeekableFileDescriptor(path: String): android.os.ParcelFileDescriptor {
        val file = java.io.File(path)
        return android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    }

    @Throws(IOException::class)
    fun getSeekableFileDescriptor(assetName: String, context: Context): android.os.ParcelFileDescriptor {
        val file = FileUtils.fileFromAsset(context, assetName)
        return android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
