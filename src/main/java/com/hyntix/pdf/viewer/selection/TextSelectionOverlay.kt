package com.hyntix.pdf.viewer.selection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Overlay view that displays text selection highlights and handles.
 * This view is placed on top of PDFView to draw selection UI.
 */
class TextSelectionOverlay(context: Context) : FrameLayout(context) {
    
    // Selection rectangles in screen coordinates
    private var selectionRects: List<RectF> = emptyList()
    
    // Handles
    private val startHandle = SelectionHandleView(context, SelectionHandleView.HandleType.START)
    private val endHandle = SelectionHandleView(context, SelectionHandleView.HandleType.END)
    
    // Paint for selection highlight
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x402196F3 // Semi-transparent blue
    }
    
    // Drag state
    private var isDraggingHandle = false
    private var activeHandle: SelectionHandleView? = null
    private var dragTouchOffset = PointF()
    private var dragStartPoint = PointF()
    
    // Callbacks
    var onStartHandleDragged: ((Float, Float) -> Unit)? = null
    var onEndHandleDragged: ((Float, Float) -> Unit)? = null
    var onDragEnded: (() -> Unit)? = null
    
    init {
        // Transparent background, allow clicks to pass through when not on handles
        setWillNotDraw(false)
        
        // Add handles (hidden by default)
        addView(startHandle, LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        addView(endHandle, LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        
        hideHandles()
    }
    
    /**
     * Update the selection display.
     * @param rects List of selection rectangles in screen coordinates
     * @param startPos Start handle position in screen coordinates
     * @param endPos End handle position in screen coordinates
     */
    fun updateSelection(rects: List<RectF>, startPos: PointF?, endPos: PointF?) {
        selectionRects = rects
        
        if (startPos != null && endPos != null && rects.isNotEmpty()) {
            startHandle.position = startPos
            endHandle.position = endPos
            showHandles()
        } else {
            hideHandles()
        }
        
        invalidate()
    }
    
    /**
     * Clear the selection display.
     */
    fun clearSelection() {
        selectionRects = emptyList()
        hideHandles()
        invalidate()
    }
    
    /**
     * Show the selection handles.
     */
    fun showHandles() {
        startHandle.visibility = View.VISIBLE
        endHandle.visibility = View.VISIBLE
    }
    
    /**
     * Hide the selection handles.
     */
    fun hideHandles() {
        startHandle.visibility = View.GONE
        endHandle.visibility = View.GONE
    }
    
    /**
     * Check if handles are visible.
     */
    fun areHandlesVisible(): Boolean {
        return startHandle.visibility == View.VISIBLE
    }
    
    /**
     * Set the selection highlight color.
     */
    fun setHighlightColor(color: Int) {
        highlightPaint.color = color
        invalidate()
    }
    
    /**
     * Set the handle color.
     */
    fun setHandleColor(color: Int) {
        startHandle.setHandleColor(color)
        endHandle.setHandleColor(color)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw selection highlights
        for (rect in selectionRects) {
            canvas.drawRect(rect, highlightPaint)
        }
    }
    
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Intercept touch if it's on a handle
        if (ev.action == MotionEvent.ACTION_DOWN && areHandlesVisible()) {
            val x = ev.x
            val y = ev.y
            
            // Check start handle
            if (isTouchOnHandle(startHandle, x, y)) {
                return true
            }
            
            // Check end handle
            if (isTouchOnHandle(endHandle, x, y)) {
                return true
            }
        }
        
        return super.onInterceptTouchEvent(ev)
    }
    


    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!areHandlesVisible()) {
            return false
        }
        
        val x = event.x
        val y = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Check which handle was touched
                when {
                    isTouchOnHandle(startHandle, x, y) -> {
                        isDraggingHandle = true
                        activeHandle = startHandle
                        // Calculate offset from touch point to the actual handle anchor position
                        dragTouchOffset.set(startHandle.position.x - x, startHandle.position.y - y)
                        return true
                    }
                    isTouchOnHandle(endHandle, x, y) -> {
                        isDraggingHandle = true
                        activeHandle = endHandle
                        // Calculate offset from touch point to the actual handle anchor position
                        dragTouchOffset.set(endHandle.position.x - x, endHandle.position.y - y)
                        return true
                    }
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingHandle && activeHandle != null) {
                    // Apply offset to get the target selection point
                    val targetX = x + dragTouchOffset.x
                    val targetY = y + dragTouchOffset.y
                    
                    when (activeHandle) {
                        startHandle -> onStartHandleDragged?.invoke(targetX, targetY)
                        endHandle -> onEndHandleDragged?.invoke(targetX, targetY)
                    }
                    return true
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingHandle) {
                    isDraggingHandle = false
                    activeHandle = null
                    onDragEnded?.invoke()
                    return true
                }
            }
        }
        
        return false
    }
    
    private fun isTouchOnHandle(handle: SelectionHandleView, x: Float, y: Float): Boolean {
        if (handle.visibility != View.VISIBLE) return false
        
        // Convert to handle's local coordinates
        val localX = x - handle.x
        val localY = y - handle.y
        
        return handle.isInHandle(localX, localY)
    }
}
