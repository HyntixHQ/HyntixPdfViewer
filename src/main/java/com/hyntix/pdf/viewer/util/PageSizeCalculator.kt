package com.hyntix.pdf.viewer.util

import com.hyntix.pdf.viewer.util.FitPolicy
import com.hyntix.pdfium.util.Size
import com.hyntix.pdfium.util.SizeF
import kotlin.math.floor

class PageSizeCalculator(
    private val fitPolicy: FitPolicy,
    private val originalMaxWidthPageSize: Size,
    private val originalMaxHeightPageSize: Size,
    private val viewSize: Size,
    private val fitEachPage: Boolean
) {
    var optimalMaxWidthPageSize: SizeF? = null
        private set
    var optimalMaxHeightPageSize: SizeF? = null
        private set
    private var widthRatio = 0f
    private var heightRatio = 0f

    init {
        calculateMaxPages()
    }

    fun calculate(pageSize: Size): SizeF {
        if (pageSize.width <= 0 || pageSize.height <= 0) {
            return SizeF(0f, 0f)
        }
        val maxWidth = if (fitEachPage) viewSize.width.toFloat() else pageSize.width * widthRatio
        val maxHeight = if (fitEachPage) viewSize.height.toFloat() else pageSize.height * heightRatio
        return when (fitPolicy) {
            FitPolicy.HEIGHT -> fitHeight(pageSize, maxHeight)
            FitPolicy.BOTH -> fitBoth(pageSize, maxWidth, maxHeight)
            else -> fitWidth(pageSize, maxWidth)
        }
    }

    private fun calculateMaxPages() {
        when (fitPolicy) {
            FitPolicy.HEIGHT -> {
                optimalMaxHeightPageSize = fitHeight(originalMaxHeightPageSize, viewSize.height.toFloat())
                heightRatio = optimalMaxHeightPageSize!!.height / originalMaxHeightPageSize.height
                optimalMaxWidthPageSize = fitHeight(
                    originalMaxWidthPageSize,
                    originalMaxWidthPageSize.height * heightRatio
                )
            }
            FitPolicy.BOTH -> {
                val localOptimalMaxWidth = fitBoth(
                    originalMaxWidthPageSize,
                    viewSize.width.toFloat(),
                    viewSize.height.toFloat()
                )
                val localWidthRatio = localOptimalMaxWidth.width / originalMaxWidthPageSize.width
                optimalMaxHeightPageSize = fitBoth(
                    originalMaxHeightPageSize,
                    originalMaxHeightPageSize.width * localWidthRatio,
                    viewSize.height.toFloat()
                )
                heightRatio = optimalMaxHeightPageSize!!.height / originalMaxHeightPageSize.height
                optimalMaxWidthPageSize = fitBoth(
                    originalMaxWidthPageSize,
                    viewSize.width.toFloat(),
                    originalMaxWidthPageSize.height * heightRatio
                )
                widthRatio = optimalMaxWidthPageSize!!.width / originalMaxWidthPageSize.width
            }
            else -> {
                optimalMaxWidthPageSize = fitWidth(originalMaxWidthPageSize, viewSize.width.toFloat())
                widthRatio = optimalMaxWidthPageSize!!.width / originalMaxWidthPageSize.width
                optimalMaxHeightPageSize = fitWidth(
                    originalMaxHeightPageSize,
                    originalMaxHeightPageSize.width * widthRatio
                )
            }
        }
    }

    private fun fitWidth(pageSize: Size, maxWidth: Float): SizeF {
        var w = pageSize.width.toFloat()
        val h = pageSize.height.toFloat()
        val ratio = w / h
        w = maxWidth
        val newH = floor((maxWidth / ratio).toDouble()).toFloat()
        return SizeF(w, newH)
    }

    private fun fitHeight(pageSize: Size, maxHeight: Float): SizeF {
        val w = pageSize.width.toFloat()
        var h = pageSize.height.toFloat()
        val ratio = h / w
        h = maxHeight
        val newW = floor((maxHeight / ratio).toDouble()).toFloat()
        return SizeF(newW, h)
    }

    private fun fitBoth(pageSize: Size, maxWidth: Float, maxHeight: Float): SizeF {
        val w = pageSize.width.toFloat()
        val h = pageSize.height.toFloat()
        val ratio = w / h
        var newW = maxWidth
        var newH = floor((maxWidth / ratio).toDouble()).toFloat()
        if (newH > maxHeight) {
            newH = maxHeight
            newW = floor((maxHeight * ratio).toDouble()).toFloat()
        }
        return SizeF(newW, newH)
    }
}
