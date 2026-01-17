package com.hyntix.pdf.viewer.source

import android.content.Context
import com.hyntix.pdfium.PdfDocument
import com.hyntix.pdfium.PdfiumCore
import java.io.IOException

interface DocumentSource {
    @Throws(IOException::class)
    fun createDocument(context: Context, core: PdfiumCore, password: String?): PdfDocument
}
