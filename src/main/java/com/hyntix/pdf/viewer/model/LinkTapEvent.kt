package com.hyntix.pdf.viewer.model

import android.graphics.RectF
import com.hyntix.pdfium.PdfDocument

data class LinkTapEvent(
    val originalX: Float,
    val originalY: Float,
    val documentX: Float,
    val documentY: Float,
    val mappedLinkRect: RectF,
    val link: com.hyntix.pdfium.PdfLink
)
