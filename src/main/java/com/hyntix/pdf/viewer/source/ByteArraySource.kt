package com.hyntix.pdf.viewer.source

import android.content.Context
import com.hyntix.pdfium.PdfDocument
import com.hyntix.pdfium.PdfiumCore
import java.io.IOException

class ByteArraySource(internal val data: ByteArray) : DocumentSource {
    @Throws(IOException::class)
    override fun createDocument(context: Context, core: PdfiumCore, password: String?): PdfDocument {
        return core.openDocument(data, password) ?: throw IOException("Failed to create document")
    }
}
