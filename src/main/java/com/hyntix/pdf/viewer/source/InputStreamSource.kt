package com.hyntix.pdf.viewer.source

import android.content.Context
import com.hyntix.pdf.viewer.util.Util
import com.hyntix.pdfium.PdfDocument
import com.hyntix.pdfium.PdfiumCore
import java.io.IOException
import java.io.InputStream

class InputStreamSource(private val inputStream: InputStream) : DocumentSource {
    @Throws(IOException::class)
    override fun createDocument(context: Context, core: PdfiumCore, password: String?): PdfDocument {
        return core.openDocument(Util.toByteArray(inputStream), password) ?: throw IOException("Failed to create document")
    }
}
