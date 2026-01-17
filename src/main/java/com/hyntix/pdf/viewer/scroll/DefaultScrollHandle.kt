package com.hyntix.pdf.viewer.scroll

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.hyntix.pdf.viewer.PDFView
import com.hyntix.pdf.viewer.util.Util

/**
 * A minimal scroll handle implementation.
 * No default styling is applied - the app should customize appearance.
 * 
 * @param inverted If true, handle appears on left/top. If false, right/bottom.
 * @param handleWidth Width of the handle in dp (default 40)
 * @param handleHeight Height of the handle in dp (default 40)
 */
class DefaultScrollHandle @JvmOverloads constructor(
    context: Context,
    private val inverted: Boolean = false,
    private val handleWidth: Int = 40,
    private val handleHeight: Int = 40
) : RelativeLayout(context), ScrollHandle {

    private var relativeHandlerMiddle = 0f
    private var textView: TextView = TextView(context)
    private var pdfView: PDFView? = null
    private var currentPos = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val hidePageScrollerRunnable = Runnable { hide() }

    init {
        visibility = INVISIBLE
        // No default styling - app should set background, colors, etc.
    }

    /**
     * Set the text color for the page number display
     */
    fun setTextColor(color: Int) {
        textView.setTextColor(color)
    }

    /**
     * Set the text size for the page number display
     */
    fun setTextSize(sizeDp: Float) {
        textView.textSize = sizeDp
    }

    override fun setupLayout(pdfView: PDFView) {
        val align: Int = if (pdfView.isSwipeVertical) {
            if (inverted) ALIGN_PARENT_LEFT else ALIGN_PARENT_RIGHT
        } else {
            if (inverted) ALIGN_PARENT_TOP else ALIGN_PARENT_BOTTOM
        }

        // Use WRAP_CONTENT to auto-adjust width based on page count text
        val lp = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(8, 8, 8, 8)

        val tvlp = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        tvlp.addRule(CENTER_IN_PARENT, TRUE)

        addView(textView, tvlp)

        lp.addRule(align)
        pdfView.addView(this, lp)

        this.pdfView = pdfView
    }

    override fun destroyLayout() {
        pdfView?.removeView(this)
    }

    override fun setScroll(position: Float) {
        if (!shown()) {
            show()
        } else {
            handler.removeCallbacks(hidePageScrollerRunnable)
        }
        if (pdfView != null) {
            setPosition(
                (if (pdfView!!.isSwipeVertical) pdfView!!.height else pdfView!!.width) * position
            )
        }
    }

    private fun setPosition(pos: Float) {
        var adjustedPos = pos
        if (adjustedPos.isInfinite() || adjustedPos.isNaN()) {
            return
        }
        val pdfViewSize: Float = if (pdfView!!.isSwipeVertical) {
            pdfView!!.height.toFloat()
        } else {
            pdfView!!.width.toFloat()
        }
        adjustedPos -= relativeHandlerMiddle

        val handleSize = Util.getDP(context, if (pdfView!!.isSwipeVertical) handleHeight else handleWidth)
        val topPadding = Util.getDP(context, 5) // Add top padding to avoid overlapping with top bar
        
        if (pdfView!!.isSwipeVertical) {
            if (adjustedPos < topPadding) {
                adjustedPos = topPadding.toFloat()
            } else if (adjustedPos > pdfViewSize - handleSize) {
                adjustedPos = pdfViewSize - handleSize
            }
        } else {
            if (adjustedPos < 0) {
                adjustedPos = 0f
            } else if (adjustedPos > pdfViewSize - handleSize) {
                adjustedPos = pdfViewSize - handleSize
            }
        }

        if (pdfView!!.isSwipeVertical) {
            y = adjustedPos
        } else {
            x = adjustedPos
        }

        calculateMiddle()
        invalidate()
    }

    private fun calculateMiddle() {
        val pos: Float
        val viewSize: Float
        val pdfViewSize: Float
        if (pdfView!!.isSwipeVertical) {
            pos = y
            viewSize = height.toFloat()
            pdfViewSize = pdfView!!.height.toFloat()
        } else {
            pos = x
            viewSize = width.toFloat()
            pdfViewSize = pdfView!!.width.toFloat()
        }
        relativeHandlerMiddle = ((pos + relativeHandlerMiddle) / pdfViewSize) * viewSize
    }

    override fun hideDelayed() {
        handler.postDelayed(hidePageScrollerRunnable, 1000)
    }

    override fun setPageNum(pageNum: Int, totalPages: Int) {
        val text = "$pageNum / $totalPages"
        if (textView.text != text) {
            textView.text = text
        }
    }

    override fun shown(): Boolean = visibility == VISIBLE

    override fun show() {
        visibility = VISIBLE
    }

    override fun hide() {
        visibility = INVISIBLE
    }

    private fun isPDFViewReady(): Boolean {
        return pdfView != null && pdfView!!.pageCount > 0 && !pdfView!!.documentFitsView()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isPDFViewReady()) {
            return super.onTouchEvent(event)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                pdfView!!.stopFling()
                handler.removeCallbacks(hidePageScrollerRunnable)
                currentPos = if (pdfView!!.isSwipeVertical) {
                    event.rawY - y
                } else {
                    event.rawX - x
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pdfView!!.isSwipeVertical) {
                    setPosition(event.rawY - currentPos + relativeHandlerMiddle)
                    pdfView!!.setPositionOffset(relativeHandlerMiddle / height.toFloat(), false)
                } else {
                    setPosition(event.rawX - currentPos + relativeHandlerMiddle)
                    pdfView!!.setPositionOffset(relativeHandlerMiddle / width.toFloat(), false)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                hideDelayed()
                pdfView!!.performPageSnap()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
