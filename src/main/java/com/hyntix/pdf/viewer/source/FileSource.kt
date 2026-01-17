package com.hyntix.pdf.viewer.source

import android.content.Context
import android.os.ParcelFileDescriptor
import com.hyntix.pdfium.PdfDocument
import com.hyntix.pdfium.PdfiumCore
import java.io.File
import java.io.IOException
import com.hyntix.pdf.viewer.util.Util

class FileSource(internal val file: File) : DocumentSource {
    @Throws(IOException::class)
    override fun createDocument(context: Context, core: PdfiumCore, password: String?): PdfDocument {
        val doc = core.openDocument(Util.getSeekableFileDescriptor(file.absolutePath).fd, password) 
        if (doc != null) return doc

        val error = core.getLastError()
        if (error.code == PdfiumCore.FPDF_ERR_PASSWORD) {
            throw com.hyntix.pdf.viewer.exception.PdfPasswordException()
        }
        
        throw IOException("Failed to create document. Error code: $error")
    }
}
