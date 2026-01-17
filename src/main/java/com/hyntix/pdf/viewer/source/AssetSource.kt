package com.hyntix.pdf.viewer.source

import android.content.Context
import android.os.ParcelFileDescriptor
import com.hyntix.pdf.viewer.util.FileUtils
import com.hyntix.pdf.viewer.util.Util
import com.hyntix.pdfium.PdfDocument
import com.hyntix.pdfium.PdfiumCore
import java.io.IOException

class AssetSource(internal val assetName: String) : DocumentSource {
    @Throws(IOException::class)
    override fun createDocument(context: Context, core: PdfiumCore, password: String?): PdfDocument {
        return core.openDocument(Util.getSeekableFileDescriptor(assetName, context).fd, password) ?: throw IOException("Failed to create document")
    }
}
