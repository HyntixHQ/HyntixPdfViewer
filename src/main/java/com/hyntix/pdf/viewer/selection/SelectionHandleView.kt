package com.hyntix.pdf.viewer.selection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.View

/**
 * A View that draws a selection handle (teardrop shape).
 * Used for start and end selection markers.
 */
class SelectionHandleView(
    context: Context,
    private val handleType: HandleType
) : View(context) {
    
    enum class HandleType {
        START,  // Handle points to the left (for start of selection)
        END     // Handle points to the right (for end of selection)
    }
    
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF2196F3.toInt() // Material Blue
    }
    
    private val pathData = "M174,47.75a254.19,254.19,0,0,0-41.45-38.3,8,8,0,0,0-9.18,0A254.19,254.19,0,0,0,82,47.75C54.51,79.32,40,112.6,40,144a88,88,0,0,0,176,0C216,112.6,201.49,79.32,174,47.75Z"
    
    private val originalPath: Path by lazy {
        androidx.core.graphics.PathParser.createPathFromPathData(pathData)
    }
    
    // Handle dimensions
    private val handleSizeDp = 44f
    
    // Current position in parent coordinates
    var position: PointF = PointF(0f, 0f)
        set(value) {
            field = value
            updatePosition()
        }
    
    init {
        // Set view size (Density aware)
        val density = context.resources.displayMetrics.density
        val sizePx = (handleSizeDp * density).toInt()
        minimumWidth = sizePx
        minimumHeight = sizePx
    }
    
    private fun buildHandlePath() {
        // No-op
    }
    
    private fun updatePosition() {
        // Center horizontally
        x = position.x - (measuredWidth / 2f)
        
        // Align vertically
        // Phosphor drop tip is at ~4% from the top of the 256x256 bounding box.
        // We also want a slight overlap with the text to feel "attached".
        // Let's shift up by ~15% of the height.
        y = position.y - (measuredHeight * 0.15f) 
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = context.resources.displayMetrics.density
        val sizePx = (handleSizeDp * density).toInt()
        setMeasuredDimension(sizePx, sizePx)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val saveCount = canvas.save()
        
        // 1. Scale standard 256x256 Path to View Size
        val scale = width / 256f 
        canvas.scale(scale, scale)
        
        // 2. Draw Path
        canvas.drawPath(originalPath, handlePaint)
        
        canvas.restore()
    }
    
    /**
     * Set the handle color.
     */
    fun setHandleColor(color: Int) {
        handlePaint.color = color
        invalidate()
    }
    
    /**
     * Get the touch hotspot center (in view coordinates).
     */
    fun getHotspotCenter(): PointF {
        return PointF(width / 2f, height / 2f)
    }
    
    /**
     * Check if a touch point (in view coordinates) is within the handle area.
     */
    fun isInHandle(touchX: Float, touchY: Float): Boolean {
        // Simple circle check for hit testing
        val centerX = width / 2f
        val centerY = height / 2f // Center of the box (drop body is roughly here)
        val radius = width / 2f
        val dx = touchX - centerX
        val dy = touchY - centerY
        return (dx * dx + dy * dy) <= (radius * radius)
    }
}
