package com.hyntix.pdf.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri

import android.os.Build
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.widget.RelativeLayout
import com.hyntix.pdf.viewer.exception.PageRenderingException
import com.hyntix.pdf.viewer.link.DefaultLinkHandler
import com.hyntix.pdf.viewer.link.LinkHandler
import com.hyntix.pdf.viewer.listener.*
import com.hyntix.pdf.viewer.model.PagePart
import com.hyntix.pdf.viewer.scroll.ScrollHandle
import com.hyntix.pdf.viewer.source.*
import com.hyntix.pdf.viewer.util.Constants
import com.hyntix.pdf.viewer.util.FitPolicy
import com.hyntix.pdf.viewer.util.MathUtils
import com.hyntix.pdf.viewer.util.SnapEdge
import com.hyntix.pdf.viewer.util.Util
import com.hyntix.pdfium.PdfDocument
import com.hyntix.pdfium.PdfiumCore
import com.hyntix.pdfium.util.Size
import com.hyntix.pdfium.util.SizeF
import java.io.File
import java.io.InputStream
import java.util.*

/**
 * It supports animations, zoom, cache, and swipe.
 *
 *
 * To fully understand this class you must know its principles :
 * - The PDF document is seen as if we always want to draw all the pages.
 * - The thing is that we only draw the visible parts.
 * - All parts are the same size, this is because we can't interrupt a native page rendering,
 * so we need these renderings to be as fast as possible, and be able to interrupt them
 * as soon as we can.
 * - The parts are loaded when the current offset or the current zoom level changes
 *
 *
 * Important :
 * - DocumentPage = A page of the PDF document.
 * - UserPage = A page as defined by the user.
 * By default, they're the same. But the user can change the pages order
 * using [.load]. In this
 * particular case, a userPage of 5 can refer to a documentPage of 17.
 */
class PDFView(context: Context, set: AttributeSet?) : RelativeLayout(context, set) {

    internal var minZoom = DEFAULT_MIN_SCALE
    internal var midZoom = DEFAULT_MID_SCALE
    internal var midZoom2 = DEFAULT_MID_SCALE_2
    internal var midZoom3 = DEFAULT_MID_SCALE_3
    internal var maxZoom = DEFAULT_MAX_SCALE

    /**
     * START - scrolling in first page direction
     * END - scrolling in last page direction
     * NONE - not scrolling
     */
    enum class ScrollDir {
        NONE, START, END
    }

    private var scrollDir = ScrollDir.NONE

    /** Rendered parts go to the cache manager  */
    internal var cacheManager: CacheManager? = null
        private set
    
    /** Disk cache for persistent tile storage across sessions */
    private var diskCacheManager: com.hyntix.pdf.viewer.cache.DiskCacheManager? = null
    
    /** Hash of the current document for cache key generation */
    internal var documentHash: String = ""
    
    /** Bitmap pool for reusing bitmaps to reduce GC pressure */
    internal var bitmapPool: com.hyntix.pdf.viewer.cache.BitmapPool? = null
        private set
    
    /** Animation manager manage all offset and zoom animation  */
    private var animationManager: AnimationManager? = null

    /** Drag manager manage all touch events  */
    internal var dragPinchManager: DragPinchManager? = null

    var pdfFile: PdfFile? = null
    
    /** The document source used to open the PDF (stored for cleanup) */
    private var documentSource: DocumentSource? = null

    /** The index of the current sequence  */
    var currentPage = 0
        private set

    /**
     * If you picture all the pages side by side in their optimal width,
     * and taking into account the zoom level, the current offset is the
     * position of the left border of the screen in this big picture
     */
    var currentXOffset = 0f
        private set

    /**
     * If you picture all the pages side by side in their optimal width,
     * and taking into account the zoom level, the current offset is the
     * position of the left border of the screen in this big picture
     */
    var currentYOffset = 0f
        private set

    /** The zoom level, always >= 1  */
    var zoom = 1f
        private set
    /** Last zoom level at which tiles were rendered. Used for cache invalidation. */
    private var lastRenderedZoom = 1f

    /** True if the PDFView has been recycled  */
    var isRecycled = true
        private set

    /** Current state of the view  */
    private var state = State.DEFAULT

    /** Async task used during the loading phase to decode a PDF document  */
    private var decodingAsyncTask: DecodingAsyncTask? = null

    /** The thread [.renderingHandler] will run on  */
    private var renderingHandlerThread: HandlerThread? = null

    /** Handler always waiting in the background and rendering tasks  */
    internal var renderingHandler: RenderingHandler? = null

    private var pagesLoader: PagesLoader? = null

    var callbacks: Callbacks = Callbacks()

    /** Paint object for drawing  */
    private var paint: Paint? = null

    /** Paint object for drawing debug stuff  */
    private var debugPaint: Paint? = null

    /** Policy for fitting pages to screen  */
    var pageFitPolicy = FitPolicy.WIDTH
        private set

    var isFitEachPage = false
        private set

    private var defaultPage = 0

    /** True if should scroll through pages vertically instead of horizontally  */
    var isSwipeVertical = true
        private set

    var isSwipeEnabled = true
        private set

    var isDoubletapEnabled = true
        private set

    private var nightMode = false

    var isPageSnap = true
        private set

    /** Pdfium core for loading and rendering PDFs  */
    private var pdfiumCore: PdfiumCore? = null

    var scrollHandle: ScrollHandle? = null
        private set

    private var isScrollHandleInit = false

    /**
     * True if bitmap should use ARGB_8888 format and take more memory
     * False if bitmap should be compressed by using RGB_565 format and take less memory
     */
    var isBestQuality = true
        private set

    /**
     * True if annotations should be rendered
     * False otherwise
     */
    var isAnnotationRendering = false
        private set

    /**
     * True if the view should render during scaling<br></br>
     * Can not be forced on older API versions (< Build.VERSION_CODES.KITKAT) as the GestureDetector does
     * not detect scrolling while scaling.<br></br>
     * False otherwise
     */
    private var renderDuringScale = false

    /** Antialiasing and bitmap filtering  */
    var isAntialiasing = true
        private set
    private val antialiasFilter =
        PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** Spacing between pages, in px  */
    var spacingPx = 0
        private set

    /** Add dynamic spacing to fit each page separately on the screen.  */
    var isAutoSpacingEnabled = false
        private set

    /** Fling a single page at a time  */
    var isPageFlingEnabled = true
        private set

    /** Pages numbers used when calling onDrawAllListener  */
    private val onDrawPagesNums: MutableList<Int> = ArrayList(10)

    /** Holds info whether view has been added to layout and has width and height  */
    private var hasSize = false

    /** Holds last used Configurator that should be loaded when view has size  */
    private var waitingDocumentConfigurator: Configurator? = null

    // Text Selection
    private var textSelectionOverlay: com.hyntix.pdf.viewer.selection.TextSelectionOverlay? = null
    internal var textSelectionManager: com.hyntix.pdf.viewer.selection.TextSelectionManager? = null
    
    /** Whether text selection is enabled */
    var isTextSelectionEnabled = false
        private set

    init {
        renderingHandlerThread = HandlerThread("PDF renderer")

        if (!isInEditMode) {
            bitmapPool = com.hyntix.pdf.viewer.cache.BitmapPool(Constants.Cache.CACHE_SIZE)
            cacheManager = CacheManager(bitmapPool)
            animationManager = AnimationManager(this)
            dragPinchManager = DragPinchManager(this, animationManager!!)
            pagesLoader = PagesLoader(this)
            paint = Paint()
            debugPaint = Paint()
            debugPaint!!.style = Paint.Style.STROKE
            pdfiumCore = PdfiumCore()
            setWillNotDraw(false)
            
            // Initialize text selection overlay
            textSelectionOverlay = com.hyntix.pdf.viewer.selection.TextSelectionOverlay(context)
            textSelectionOverlay?.let { overlay ->
                addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                overlay.visibility = GONE // Hidden until enabled
                
                // Set up handle drag callbacks
                overlay.onStartHandleDragged = { x, y ->
                    handleSelectionHandleDrag(true, x, y)
                }
                overlay.onEndHandleDragged = { x, y ->
                    handleSelectionHandleDrag(false, x, y)
                }
                overlay.onDragEnded = {
                    updateSelectionDisplay()
                }
            }
        }
    }
    
    /**
     * Enable or disable text selection.
     */
    fun setTextSelectionEnabled(enabled: Boolean) {
        isTextSelectionEnabled = enabled
        textSelectionOverlay?.visibility = if (enabled) VISIBLE else GONE
        if (!enabled) {
            clearTextSelection()
        }
    }
    
    /**
     * Start text selection at the given screen coordinates.
     */
    fun startTextSelection(screenX: Float, screenY: Float): Boolean {
        if (!isTextSelectionEnabled || pdfFile == null) return false
        
        // Initialize manager if needed
        if (textSelectionManager == null) {
            textSelectionManager = com.hyntix.pdf.viewer.selection.TextSelectionManager(pdfFile!!)
        }
        
        // Convert screen to PDF coordinates
        val pagePoint = getPagePoint(screenX, screenY) ?: return false
        val page = pagePoint.first
        val pdfX = pagePoint.second.x
        val pdfY = pagePoint.second.y
        
        val success = textSelectionManager!!.startSelectionAt(page, pdfX, pdfY)
        if (success) {
            updateSelectionDisplay()
            // Custom UI notified via callbacks
        }
        return success
    }
    

    /**
     * Clear the current text selection.
     */
    fun clearTextSelection() {
        textSelectionManager?.clearSelection()
        textSelectionOverlay?.clearSelection()
        // Callback is called to notify listeners (e.g. UI) that selection is cleared
        callbacks.callOnSelectionCleared()
        invalidate()
    }
    
    /**
     * Get the currently selected text.
     */
    fun getSelectedText(): String {
        return textSelectionManager?.selectedText ?: ""
    }
    
    /**
     * Handle selection handle drag.
     */
    private fun handleSelectionHandleDrag(isStartHandle: Boolean, screenX: Float, screenY: Float) {
        val pagePoint = getPagePoint(screenX, screenY) ?: return
        val pdfX = pagePoint.second.x
        val pdfY = pagePoint.second.y
        
        if (isStartHandle) {
            textSelectionManager?.updateStartHandle(pdfX, pdfY)
        } else {
            textSelectionManager?.updateEndHandle(pdfX, pdfY)
        }
        updateSelectionDisplay()
    }
    
    /**
     * Update the selection overlay display.
     */
    internal fun updateSelectionDisplay() {
        val manager = textSelectionManager ?: return
        val overlay = textSelectionOverlay ?: return
        val file = pdfFile ?: return
        
        if (!manager.hasSelection()) {
            overlay.clearSelection()
            return
        }
        
        val page = manager.selectionPage
        val pdfRects = manager.selectionRects
        
        // Convert PDF rects to screen coordinates
        val pageSize = file.getScaledPageSize(page, zoom)
        val pageOffsetX: Float
        val pageOffsetY: Float
        if (isSwipeVertical) {
            pageOffsetX = file.getSecondaryPageOffset(page, zoom)
            pageOffsetY = file.getPageOffset(page, zoom)
        } else {
            pageOffsetY = file.getSecondaryPageOffset(page, zoom)
            pageOffsetX = file.getPageOffset(page, zoom)
        }
        
        val screenRects = pdfRects.map { pdfRect ->
            val mapped = file.mapRectToDevice(
                page, 0, 0,
                pageSize.width.toInt(), pageSize.height.toInt(),
                pdfRect
            )
            // Add page offset and current scroll offset
            RectF(
                mapped.left + pageOffsetX + currentXOffset,
                mapped.top + pageOffsetY + currentYOffset,
                mapped.right + pageOffsetX + currentXOffset,
                mapped.bottom + pageOffsetY + currentYOffset
            )
        }
        
        // Get handle positions
        val handlePositions = manager.getHandlePositions()
        var startPos: PointF? = null
        var endPos: PointF? = null
        
        if (handlePositions != null) {
            // Convert handle positions to screen coordinates
            val startMapped = file.mapRectToDevice(
                page, 0, 0,
                pageSize.width.toInt(), pageSize.height.toInt(),
                RectF(handlePositions.first.x, handlePositions.first.y, 
                      handlePositions.first.x, handlePositions.first.y)
            )
            val endMapped = file.mapRectToDevice(
                page, 0, 0,
                pageSize.width.toInt(), pageSize.height.toInt(),
                RectF(handlePositions.second.x, handlePositions.second.y,
                      handlePositions.second.x, handlePositions.second.y)
            )
            startPos = PointF(
                startMapped.left + pageOffsetX + currentXOffset,
                startMapped.top + pageOffsetY + currentYOffset
            )
            endPos = PointF(
                endMapped.left + pageOffsetX + currentXOffset,
                endMapped.top + pageOffsetY + currentYOffset
            )
        }
        
        overlay.updateSelection(screenRects, startPos, endPos)
        
        // Notify callback
        callbacks.callOnTextSelected(manager.selectedText, pdfRects, page)
    }

    // Helper to select all text on a page (can be called from external UI)
    fun selectAllText(page: Int) {
        textSelectionManager?.selectAll(page)
        updateSelectionDisplay()
    }

    /**
     * Update selection end point during drag.
     */
    fun updateSelectionDrag(screenX: Float, screenY: Float) {
        if (!isTextSelectionEnabled || pdfFile == null || textSelectionManager == null) return
        
        val pagePoint = getPagePoint(screenX, screenY) ?: return
        val pdfX = pagePoint.second.x
        val pdfY = pagePoint.second.y
        
        textSelectionManager?.updateSelectionTo(pdfX, pdfY)
        updateSelectionDisplay()
        
        // Notify callback (optional during drag, maybe too frequent?)
        // callbacks.callOnTextSelected(...) 
    }
    
    /**
     * Set color for selection handles.
     */
    fun setSelectionHandleColor(color: Int) {
        textSelectionOverlay?.setHandleColor(color)
    }
    
    /**
     * Set color for selection highlight.
     */
    fun setSelectionHighlightColor(color: Int) {
        textSelectionOverlay?.setHighlightColor(color)
    }

    private fun load(docSource: DocumentSource, password: String?) {
        load(docSource, password, null)
    }

    private fun load(docSource: DocumentSource, password: String?, userPages: IntArray?) {
        if (!isRecycled) {
            throw IllegalStateException("Don't call load on a PDF View without recycling it first.")
        }
        isRecycled = false
        // Store document source for cleanup
        documentSource = docSource
        // Start decoding document
        decodingAsyncTask = DecodingAsyncTask(docSource, password, userPages, this, pdfiumCore!!)
        decodingAsyncTask!!.execute()
    }

    /**
     * Go to the given page.
     *
     * @param page Page index.
     */
    fun jumpTo(page: Int, withAnimation: Boolean) {
        if (pdfFile == null) {
            return
        }
        var page = page
        page = pdfFile!!.determineValidPageNumberFrom(page)
        val offset = if (page == 0) 0f else -pdfFile!!.getPageOffset(page, zoom)
        if (isSwipeVertical) {
            if (withAnimation) {
                animationManager!!.startYAnimation(currentYOffset, offset)
            } else {
                moveTo(currentXOffset, offset)
            }
        } else {
            if (withAnimation) {
                animationManager!!.startXAnimation(currentXOffset, offset)
            } else {
                moveTo(offset, currentYOffset)
            }
        }
        showPage(page)
    }

    fun jumpTo(page: Int) {
        jumpTo(page, false)
    }

    fun showPage(pageNb: Int) {
        if (isRecycled) {
            return
        }
        // Check the page number and makes the
        // difference between UserPages and DocumentPages
        val validPageNb = pdfFile!!.determineValidPageNumberFrom(pageNb)
        currentPage = validPageNb
        loadPages()
        if (scrollHandle != null && !documentFitsView()) {
            scrollHandle!!.setPageNum(currentPage + 1, pdfFile!!.pagesCount)
        }
        callbacks.callOnPageChange(currentPage, pdfFile!!.pagesCount)
    }

    /**
     * Get current position as ratio of document length to visible area.
     * 0 means that document start is visible, 1 that document end is visible
     *
     * @return offset between 0 and 1
     */
    val positionOffset: Float
        get() {
            val offset: Float
            offset = if (isSwipeVertical) {
                -currentYOffset / (pdfFile!!.getDocLen(zoom) - height)
            } else {
                -currentXOffset / (pdfFile!!.getDocLen(zoom) - width)
            }
            return MathUtils.limit(offset, 0f, 1f)
        }

    /**
     * @param progress   must be between 0 and 1
     * @param moveHandle whether to move scroll handle
     * @see PDFView.positionOffset
     */
    fun setPositionOffset(progress: Float, moveHandle: Boolean) {
        if (isSwipeVertical) {
            moveTo(
                currentXOffset,
                (-pdfFile!!.getDocLen(zoom) + height) * progress,
                moveHandle
            )
        } else {
            moveTo(
                (-pdfFile!!.getDocLen(zoom) + width) * progress,
                currentYOffset,
                moveHandle
            )
        }
        loadPageByOffset()
    }

    fun setPositionOffset(progress: Float) {
        setPositionOffset(progress, true)
    }

    fun stopFling() {
        animationManager!!.stopFling()
    }

    val pageCount: Int
        get() = if (pdfFile == null) {
            0
        } else pdfFile!!.pagesCount

    fun setSwipeEnabled(enableSwipe: Boolean) {
        this.isSwipeEnabled = enableSwipe
    }

    fun setNightMode(nightMode: Boolean) {
        this.nightMode = nightMode
        if (nightMode) {
            val colorMatrixInverted = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val filter = ColorMatrixColorFilter(colorMatrixInverted)
            paint!!.colorFilter = filter
        } else {
            paint!!.colorFilter = null
        }
    }

    fun enableDoubletap(enableDoubletap: Boolean) {
        this.isDoubletapEnabled = enableDoubletap
    }

    fun onPageError(ex: PageRenderingException) {
        if (!callbacks.callOnPageError(ex.page, ex.cause)) {
            Log.e(TAG, "Cannot open page " + ex.page, ex.cause)
        }
    }

    fun recycle() {
        waitingDocumentConfigurator = null
        animationManager!!.stopAll()
        dragPinchManager!!.disable()

        // Stop tasks
        if (renderingHandler != null) {
            renderingHandler!!.stop()
            renderingHandler!!.removeMessages(RenderingHandler.MSG_RENDER_TASK)
        }
        if (decodingAsyncTask != null) {
            decodingAsyncTask!!.cancel()
        }

        // Clear caches
        cacheManager!!.recycle()
        
        // Shutdown disk cache executor
        diskCacheManager?.shutdown()
        diskCacheManager = null
        
        // Close document source (releases ParcelFileDescriptor for UriSource)
        if (documentSource is UriSource) {
            (documentSource as UriSource).close()
        }
        documentSource = null
        
        if (scrollHandle != null && isScrollHandleInit) {
            scrollHandle!!.destroyLayout()
        }
        if (pdfFile != null) {
            pdfFile!!.dispose()
            pdfFile = null
        }
        renderingHandler = null
        scrollHandle = null
        isScrollHandleInit = false
        currentXOffset = 0f
        currentYOffset = 0f
        zoom = 1f
        isRecycled = true
        callbacks = Callbacks()
        state = State.DEFAULT
    }

    /** Handle fling animation  */
    override fun computeScroll() {
        super.computeScroll()
        if (isInEditMode) {
            return
        }
        animationManager!!.computeFling()
    }

    override fun onDetachedFromWindow() {
        recycle()
        if (renderingHandlerThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                renderingHandlerThread!!.quitSafely()
            } else {
                renderingHandlerThread!!.quit()
            }
            renderingHandlerThread = null
        }
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        hasSize = true
        if (waitingDocumentConfigurator != null) {
            waitingDocumentConfigurator!!.load()
        }
        if (isInEditMode || state != State.SHOWN) {
            return
        }

        // calculates the position of the point which in the center of view relative to big strip
        val centerPointInStripXOffset = -currentXOffset + oldw * 0.5f
        val centerPointInStripYOffset = -currentYOffset + oldh * 0.5f
        val relativeCenterPointInStripXOffset: Float
        val relativeCenterPointInStripYOffset: Float
        if (isSwipeVertical) {
            relativeCenterPointInStripXOffset =
                centerPointInStripXOffset / pdfFile!!.maxPageWidth
            relativeCenterPointInStripYOffset =
                centerPointInStripYOffset / pdfFile!!.getDocLen(zoom)
        } else {
            relativeCenterPointInStripXOffset =
                centerPointInStripXOffset / pdfFile!!.getDocLen(zoom)
            relativeCenterPointInStripYOffset =
                centerPointInStripYOffset / pdfFile!!.maxPageHeight
        }
        animationManager!!.stopAll()
        pdfFile!!.recalculatePageSizes(Size(w, h))
        if (isSwipeVertical) {
            currentXOffset =
                -relativeCenterPointInStripXOffset * pdfFile!!.maxPageWidth + w * 0.5f
            currentYOffset =
                -relativeCenterPointInStripYOffset * pdfFile!!.getDocLen(zoom) + h * 0.5f
        } else {
            currentXOffset =
                -relativeCenterPointInStripXOffset * pdfFile!!.getDocLen(zoom) + w * 0.5f
            currentYOffset =
                -relativeCenterPointInStripYOffset * pdfFile!!.maxPageHeight + h * 0.5f
        }
        moveTo(currentXOffset, currentYOffset)
        loadPageByOffset()
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        if (pdfFile == null) {
            return true
        }
        if (isSwipeVertical) {
            if (direction < 0 && currentXOffset < 0) {
                return true
            } else if (direction > 0 && currentXOffset + toCurrentScale(pdfFile!!.maxPageWidth) > width) {
                return true
            }
        } else {
            if (direction < 0 && currentXOffset < 0) {
                return true
            } else if (direction > 0 && currentXOffset + pdfFile!!.getDocLen(zoom) > width) {
                return true
            }
        }
        return false
    }

    override fun canScrollVertically(direction: Int): Boolean {
        if (pdfFile == null) {
            return true
        }
        if (isSwipeVertical) {
            if (direction < 0 && currentYOffset < 0) {
                return true
            } else if (direction > 0 && currentYOffset + pdfFile!!.getDocLen(zoom) > height) {
                return true
            }
        } else {
            if (direction < 0 && currentYOffset < 0) {
                return true
            } else if (direction > 0 && currentYOffset + toCurrentScale(pdfFile!!.maxPageHeight) > height) {
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        if (isInEditMode) {
            return
        }
        // As I said in this class javadoc, we can think of this canvas as a huge
        // strip on which we draw all the images. We actually only draw the rendered
        // parts, of course, but we render them in the place they belong in this huge
        // strip.

        // That's where Canvas.translate(x, y) becomes very helpful.
        // This is the situation :
        //  _______________________________________________
        // |   			 |					 			   |
        // | the actual  |					The big strip  |
        // |	canvas	 | 								   |
        // |_____________|								   |
        // |_______________________________________________|
        //
        // If the rendered part is on the bottom right corner of the strip
        // we can draw it but we won't see it because the canvas is not big enough.

        // But if we call translate(-X, -Y) on the canvas just before drawing the object :
        //  _______________________________________________
        // |   			  					  _____________|
        // |   The big strip     			 |			   |
        // |		    					 |	the actual |
        // |								 |	canvas	   |
        // |_________________________________|_____________|
        //
        // The object will be on the canvas.
        // This technique is massively used in this method, and allows
        // abstraction of the screen position when rendering the parts.

        // Draws background
        if (isAntialiasing) {
            canvas.drawFilter = antialiasFilter
        }
        val bg = background
        if (bg == null) {
            canvas.drawColor(if (nightMode) Color.BLACK else Color.WHITE)
        } else {
            bg.draw(canvas)
        }
        if (isRecycled) {
            return
        }
        if (state != State.SHOWN) {
            return
        }

        // Moves the canvas before drawing any element
        val currentXOffset = currentXOffset
        val currentYOffset = currentYOffset
        canvas.translate(currentXOffset, currentYOffset)

        // Draws thumbnails
        for (part in cacheManager!!.thumbnails) {
            drawPart(canvas, part)
        }

        // Draws parts
        for (part in cacheManager!!.pageParts) {
            drawPart(canvas, part)
            if (callbacks.onDrawAll != null && !onDrawPagesNums.contains(part.page)) {
                onDrawPagesNums.add(part.page)
            }
        }
        for (page in onDrawPagesNums) {
            drawWithListener(canvas, page, callbacks.onDrawAll)
        }
        onDrawPagesNums.clear()
        drawWithListener(canvas, currentPage, callbacks.onDraw)

        // Restores the canvas position
        canvas.translate(-currentXOffset, -currentYOffset)
    }

    private fun drawWithListener(canvas: Canvas, page: Int, listener: OnDrawListener?) {
        if (listener != null) {
            val translateX: Float
            val translateY: Float
            if (isSwipeVertical) {
                translateX = 0f
                translateY = pdfFile!!.getPageOffset(page, zoom)
            } else {
                translateY = 0f
                translateX = pdfFile!!.getPageOffset(page, zoom)
            }
            canvas.translate(translateX, translateY)
            val size = pdfFile!!.getPageSize(page)
            listener.onLayerDrawn(
                canvas,
                toCurrentScale(size.width),
                toCurrentScale(size.height),
                page
            )
            canvas.translate(-translateX, -translateY)
        }
    }

    /** Draw a given PagePart on the canvas  */
    private fun drawPart(canvas: Canvas, part: PagePart) {
        // Can seem strange, but avoid lot of calls
        val pageRelativeBounds = part.pageRelativeBounds
        val renderedBitmap = part.renderedBitmap
        if (renderedBitmap == null || renderedBitmap.isRecycled) {
            return
        }

        // Move to the target page
        var localTranslationX = 0f
        var localTranslationY = 0f
        val size = pdfFile!!.getPageSize(part.page)
        if (isSwipeVertical) {
            localTranslationY = pdfFile!!.getPageOffset(part.page, zoom)
            val maxWidth = pdfFile!!.maxPageWidth
            localTranslationX = toCurrentScale(maxWidth - size.width) / 2
        } else {
            localTranslationX = pdfFile!!.getPageOffset(part.page, zoom)
            val maxHeight = pdfFile!!.maxPageHeight
            localTranslationY = toCurrentScale(maxHeight - size.height) / 2
        }
        canvas.translate(localTranslationX, localTranslationY)
        val srcRect = Rect(0, 0, renderedBitmap.width, renderedBitmap.height)
        val offsetX = toCurrentScale(pageRelativeBounds.left * size.width)
        val offsetY = toCurrentScale(pageRelativeBounds.top * size.height)
        val width = toCurrentScale(pageRelativeBounds.width() * size.width)
        val height = toCurrentScale(pageRelativeBounds.height() * size.height)

        // If we use float values for this rectangle, there will be
        // a possible gap between page parts, especially when
        // the zoom level is high.
        val dstRect = RectF(
            offsetX.toInt().toFloat(), offsetY.toInt().toFloat(),
            (offsetX + width).toInt().toFloat(),
            (offsetY + height).toInt().toFloat()
        )

        // Check if bitmap is in the screen
        val translationX = currentXOffset + localTranslationX
        val translationY = currentYOffset + localTranslationY
        if (translationX + dstRect.left >= getWidth() || translationX + dstRect.right <= 0 || translationY + dstRect.top >= getHeight() || translationY + dstRect.bottom <= 0) {
            canvas.translate(-localTranslationX, -localTranslationY)
            return
        }
        canvas.drawBitmap(renderedBitmap, srcRect, dstRect, paint)
        if (Constants.DEBUG_MODE) {
            debugPaint!!.color = if (part.page % 2 == 0) Color.RED else Color.BLUE
            canvas.drawRect(dstRect, debugPaint!!)
        }

        // Restore the canvas position
        canvas.translate(-localTranslationX, -localTranslationY)
    }

    /**
     * Load all the parts around the center of the screen,
     * taking into account X and Y offsets, zoom level, and
     * the current page displayed
     */
    fun loadPages() {
        if (pdfFile == null || renderingHandler == null) {
            return
        }

        // Cancel all current tasks
        renderingHandler!!.removeMessages(RenderingHandler.MSG_RENDER_TASK)
        cacheManager!!.makeANewSet()
        pagesLoader!!.loadPages()
        redraw()
    }

    /** Called when the PDF is loaded  */
    fun loadComplete(pdfFile: PdfFile) {
        state = State.LOADED
        this.pdfFile = pdfFile
        if (!renderingHandlerThread!!.isAlive) {
            renderingHandlerThread!!.start()
        }
        renderingHandler = RenderingHandler(renderingHandlerThread!!.looper, this, diskCacheManager)
        renderingHandler!!.start()
        if (scrollHandle != null) {
            scrollHandle!!.setupLayout(this)
            isScrollHandleInit = true
        }
        dragPinchManager!!.enable()
        callbacks.callOnLoadComplete(pdfFile.pagesCount)
        jumpTo(defaultPage, false)
    }

    fun loadError(t: Throwable) {
        state = State.ERROR
        // store reference, because callbacks will be cleared in recycle() method
        val onErrorListener = callbacks.onError
        recycle()
        invalidate()
        if (onErrorListener != null) {
            onErrorListener.onError(t)
        } else {
            Log.e(TAG, "load pdf error", t)
        }
    }

    fun redraw() {
        invalidate()
    }

    /**
     * Called when a rendering task is over and
     * a PagePart has been freshly created.
     *
     * @param part The created PagePart.
     */
    fun onBitmapRendered(part: PagePart) {
        // when it is first rendered part
        if (state == State.LOADED) {
            state = State.SHOWN
            callbacks.callOnRender(pdfFile!!.pagesCount)
        }
        if (part.isThumbnail) {
            cacheManager!!.cacheThumbnail(part)
        } else {
            cacheManager!!.cachePart(part)
        }
        redraw()
    }

    fun moveTo(offsetX: Float, offsetY: Float) {
        moveTo(offsetX, offsetY, true)
    }

    /**
     * Move to the given X and Y offsets, but check them ahead of time
     * to be sure not to go outside the the big strip.
     *
     * @param offsetX    The big strip X offset to use as the left border of the screen.
     * @param offsetY    The big strip Y offset to use as the right border of the screen.
     * @param moveHandle whether to move scroll handle or not
     */
    fun moveTo(offsetX: Float, offsetY: Float, moveHandle: Boolean) {
        var offsetX = offsetX
        var offsetY = offsetY
        if (isSwipeVertical) {
            // Check X offset
            val scaledPageWidth = toCurrentScale(pdfFile!!.maxPageWidth)
            if (scaledPageWidth < width) {
                offsetX = width / 2 - scaledPageWidth / 2
            } else {
                if (offsetX > 0) {
                    offsetX = 0f
                } else if (offsetX + scaledPageWidth < width) {
                    offsetX = width - scaledPageWidth
                }
            }

            // Check Y offset
            val contentHeight = pdfFile!!.getDocLen(zoom)
            if (contentHeight < height) { // whole document height visible on screen
                offsetY = (height - contentHeight) / 2
            } else {
                if (offsetY > 0) { // top visible
                    offsetY = 0f
                } else if (offsetY + contentHeight < height) { // bottom visible
                    offsetY = -contentHeight + height
                }
            }
            scrollDir = if (offsetY < currentYOffset) {
                ScrollDir.END
            } else if (offsetY > currentYOffset) {
                ScrollDir.START
            } else {
                ScrollDir.NONE
            }
        } else {
            // Check Y offset
            val scaledPageHeight = toCurrentScale(pdfFile!!.maxPageHeight)
            if (scaledPageHeight < height) {
                offsetY = height / 2 - scaledPageHeight / 2
            } else {
                if (offsetY > 0) {
                    offsetY = 0f
                } else if (offsetY + scaledPageHeight < height) {
                    offsetY = height - scaledPageHeight
                }
            }

            // Check X offset
            val contentWidth = pdfFile!!.getDocLen(zoom)
            if (contentWidth < width) { // whole document width visible on screen
                offsetX = (width - contentWidth) / 2
            } else {
                if (offsetX > 0) { // left visible
                    offsetX = 0f
                } else if (offsetX + contentWidth < width) { // right visible
                    offsetX = -contentWidth + width
                }
            }
            scrollDir = if (offsetX < currentXOffset) {
                ScrollDir.END
            } else if (offsetX > currentXOffset) {
                ScrollDir.START
            } else {
                ScrollDir.NONE
            }
        }
        currentXOffset = offsetX
        currentYOffset = offsetY
        val positionOffset = positionOffset
        if (moveHandle && scrollHandle != null && !documentFitsView()) {
            scrollHandle!!.setScroll(positionOffset)
        }
        callbacks.callOnPageScroll(currentPage, positionOffset)
        updateSelectionDisplay()
        redraw()
    }

    fun loadPageByOffset() {
        if (0 == pdfFile!!.pagesCount) {
            return
        }
        val offset: Float
        val screenCenter: Float
        if (isSwipeVertical) {
            offset = currentYOffset
            screenCenter = height.toFloat() / 2
        } else {
            offset = currentXOffset
            screenCenter = width.toFloat() / 2
        }
        val page = pdfFile!!.getPageAtOffset(-(offset - screenCenter), zoom)
        if (page >= 0 && page <= pdfFile!!.pagesCount - 1 && page != currentPage) {
            showPage(page)
        } else {
            loadPages()
        }
    }

    /**
     * Animate to the nearest snapping position for the current SnapPolicy
     */
    fun performPageSnap() {
        if (!isPageSnap || pdfFile == null || pdfFile!!.pagesCount == 0) {
            return
        }
        val centerPage = findFocusPage(currentXOffset, currentYOffset)
        val edge = findSnapEdge(centerPage)
        if (edge == SnapEdge.NONE) {
            return
        }
        val offset = snapOffsetForPage(centerPage, edge)
        if (isSwipeVertical) {
            animationManager!!.startYAnimation(currentYOffset, -offset)
        } else {
            animationManager!!.startXAnimation(currentXOffset, -offset)
        }
    }

    /**
     * Find the edge to snap to when showing the specified page
     */
    fun findSnapEdge(page: Int): SnapEdge {
        if (!isPageSnap || page < 0) {
            return SnapEdge.NONE
        }
        val currentOffset = if (isSwipeVertical) currentYOffset else currentXOffset
        val offset = -pdfFile!!.getPageOffset(page, zoom)
        val length = if (isSwipeVertical) height else width
        val pageLength = pdfFile!!.getPageLength(page, zoom)
        return if (length >= pageLength) {
            SnapEdge.CENTER
        } else if (currentOffset >= offset) {
            SnapEdge.START
        } else if (offset - pageLength > currentOffset - length) {
            SnapEdge.END
        } else {
            SnapEdge.NONE
        }
    }

    /**
     * Get the offset to move to in order to snap to the page
     */
    fun snapOffsetForPage(pageIndex: Int, edge: SnapEdge): Float {
        var offset = pdfFile!!.getPageOffset(pageIndex, zoom)
        val length = (if (isSwipeVertical) height else width).toFloat()
        val pageLength = pdfFile!!.getPageLength(pageIndex, zoom)
        if (edge == SnapEdge.CENTER) {
            offset = offset - length / 2f + pageLength / 2f
        } else if (edge == SnapEdge.END) {
            offset = offset - length + pageLength
        }
        return offset
    }

    fun findFocusPage(xOffset: Float, yOffset: Float): Int {
        val currOffset = if (isSwipeVertical) yOffset else xOffset
        val length = (if (isSwipeVertical) height else width).toFloat()
        // make sure first and last page can be found
        if (currOffset > -1) {
            return 0
        } else if (currOffset < -pdfFile!!.getDocLen(zoom) + length + 1) {
            return pdfFile!!.pagesCount - 1
        }
        // else find page in center
        val center = currOffset - length / 2f
        return pdfFile!!.getPageAtOffset(-center, zoom)
    }

    /**
     * @return true if single page fills the entire screen in the scrolling direction
     */
    fun pageFillsScreen(): Boolean {
        val start = -pdfFile!!.getPageOffset(currentPage, zoom)
        val end = start - pdfFile!!.getPageLength(currentPage, zoom)
        return if (isSwipeVertical) {
            start > currentYOffset && end < currentYOffset - height
        } else {
            start > currentXOffset && end < currentXOffset - width
        }
    }

    /**
     * Move relatively to the current position.
     *
     * @param dx The X difference you want to apply.
     * @param dy The Y difference you want to apply.
     * @see .moveTo
     */
    fun moveRelativeTo(dx: Float, dy: Float) {
        moveTo(currentXOffset + dx, currentYOffset + dy)
    }

    /**
     * Change the zoom level.\n     * Cache is NOT invalidated - natural LRU eviction handles tile replacement.
     */
    fun zoomTo(zoom: Float) {
        this.zoom = zoom
    }

    /**
     * Change the zoom level, relatively to a pivot point.
     * It will call moveTo() to make sure the given point stays
     * in the middle of the screen.
     *
     * @param zoom  The zoom level.
     * @param pivot The point on the screen that should stays.
     */
    fun zoomCenteredTo(zoom: Float, pivot: PointF) {
        val dzoom = zoom / this.zoom
        zoomTo(zoom)
        var baseX = currentXOffset * dzoom
        var baseY = currentYOffset * dzoom
        baseX += pivot.x - pivot.x * dzoom
        baseY += pivot.y - pivot.y * dzoom
        moveTo(baseX, baseY)
        updateSelectionDisplay()
    }

    /**
     * @see .zoomCenteredTo
     */
    fun zoomCenteredRelativeTo(dzoom: Float, pivot: PointF) {
        zoomCenteredTo(zoom * dzoom, pivot)
    }

    /**
     * Checks if whole document can be displayed on screen, doesn't include zoom
     *
     * @return true if whole document can displayed at once, false otherwise
     */
    fun documentFitsView(): Boolean {
        val len = pdfFile!!.getDocLen(1f)
        return if (isSwipeVertical) {
            len < height
        } else {
            len < width
        }
    }

    fun fitToWidth(page: Int) {
        if (state != State.SHOWN) {
            Log.e(TAG, "Cannot fit, document not rendered yet")
            return
        }
        zoomTo(width / pdfFile!!.getPageSize(page).width)
        jumpTo(page)
    }

    fun getPageSize(pageIndex: Int): SizeF {
        return if (pdfFile == null) {
            SizeF(0f, 0f)
        } else pdfFile!!.getPageSize(pageIndex)
    }

    fun toRealScale(size: Float): Float {
        return size / zoom
    }

    fun toCurrentScale(size: Float): Float {
        return size * zoom
    }

    val isZooming: Boolean
        get() = zoom != minZoom

    private fun setDefaultPage(defaultPage: Int) {
        this.defaultPage = defaultPage
    }

    fun resetZoom() {
        zoomTo(minZoom)
    }

    fun resetZoomWithAnimation() {
        zoomWithAnimation(minZoom)
    }

    fun zoomWithAnimation(centerX: Float, centerY: Float, scale: Float) {
        animationManager!!.startZoomAnimation(centerX, centerY, zoom, scale)
    }

    fun zoomWithAnimation(scale: Float) {
        animationManager!!.startZoomAnimation(
            width / 2f,
            height / 2f,
            zoom,
            scale
        )
    }

    private fun setScrollHandle(scrollHandle: ScrollHandle?) {
        this.scrollHandle = scrollHandle
    }

    /**
     * Get page number at given offset
     *
     * @param positionOffset scroll offset between 0 and 1
     * @return page number at given offset, starting from 0
     */
    fun getPageAtPositionOffset(positionOffset: Float): Int {
        return pdfFile!!.getPageAtOffset(
            pdfFile!!.getDocLen(zoom) * positionOffset,
            zoom
        )
    }

    fun getMinZoom(): Float {
        return minZoom
    }

    fun setMinZoom(minZoom: Float) {
        this.minZoom = minZoom
    }

    fun getMidZoom(): Float {
        return midZoom
    }

    fun setMidZoom(midZoom: Float) {
        this.midZoom = midZoom
    }

    fun getMaxZoom(): Float {
        return maxZoom
    }

    fun setMaxZoom(maxZoom: Float) {
        this.maxZoom = maxZoom
    }

    fun useBestQuality(bestQuality: Boolean) {
        isBestQuality = bestQuality
    }

    fun setSwipeVertical(swipeVertical: Boolean) {
        isSwipeVertical = swipeVertical
    }

    fun enableAnnotationRendering(annotationRendering: Boolean) {
        isAnnotationRendering = annotationRendering
    }

    fun enableRenderDuringScale(renderDuringScale: Boolean) {
        this.renderDuringScale = renderDuringScale
    }

    fun enableAntialiasing(enableAntialiasing: Boolean) {
        isAntialiasing = enableAntialiasing
    }

    fun setPageFling(pageFling: Boolean) {
        isPageFlingEnabled = pageFling
    }

    private fun setSpacing(spacingDp: Int) {
        this.spacingPx = Util.getDP(context, spacingDp)
    }

    private fun setAutoSpacing(autoSpacing: Boolean) {
        isAutoSpacingEnabled = autoSpacing
    }

    private fun setPageFitPolicy(pageFitPolicy: FitPolicy) {
        this.pageFitPolicy = pageFitPolicy
    }

    private fun setFitEachPage(fitEachPage: Boolean) {
        isFitEachPage = fitEachPage
    }

    fun setPageSnap(pageSnap: Boolean) {
        isPageSnap = pageSnap
    }

    fun doRenderDuringScale(): Boolean {
        return renderDuringScale
    }

    /** Returns null if document is not loaded  */
    val documentMeta: com.hyntix.pdfium.PdfDocument?
        get() = pdfFile?.metaData

    /** Will be empty until document is loaded  */
    val tableOfContents: List<com.hyntix.pdfium.PdfBookmark>
        get() = if (pdfFile == null) {
            java.util.Collections.emptyList()
        } else pdfFile!!.bookmarks

    /** Returns TOC as library PdfBookmark wrapper (easier to use in app code) */
    val bookmarks: List<com.hyntix.pdf.viewer.model.PdfBookmark>
        get() = if (pdfFile == null) {
            java.util.Collections.emptyList()
        } else com.hyntix.pdf.viewer.model.PdfBookmark.fromPdfiumBookmarks(pdfFile!!.bookmarks)

    /** Will be empty until document is loaded  */
    fun getLinks(page: Int): List<com.hyntix.pdfium.PdfLink> {
        return if (pdfFile == null) {
            java.util.Collections.emptyList()
        } else pdfFile!!.getPageLinks(page)
    }
    
    /**
     * Save the current state of the PDF viewer.
     * Use this to persist state across configuration changes (rotation, theme change).
     * 
     * @return PdfViewState containing current page, zoom, and scroll offsets
     */
    fun saveState(): com.hyntix.pdf.viewer.model.PdfViewState {
        return com.hyntix.pdf.viewer.model.PdfViewState(
            currentPage = currentPage,
            zoom = zoom,
            offsetX = currentXOffset,
            offsetY = currentYOffset
        )
    }
    
    /**
     * Restore a previously saved state.
     * Call this after loading the document (e.g., in onLoadComplete callback).
     * 
     * @param state The saved PdfViewState to restore
     * @param animate Whether to animate the transition (default: false)
     */
    fun restoreState(state: com.hyntix.pdf.viewer.model.PdfViewState, animate: Boolean = false) {
        if (pdfFile == null) return
        
        // Restore zoom level first
        zoomTo(state.zoom)
        
        // Jump to the saved page
        jumpTo(state.currentPage, animate)
        
        // Restore scroll offsets
        moveTo(state.offsetX, state.offsetY, animate)
    }



    /** Use an asset file as the pdf source  */
    fun fromAsset(assetName: String): Configurator {
        return Configurator(AssetSource(assetName))
    }

    /** Use a file as the pdf source  */
    fun fromFile(file: File): Configurator {
        return Configurator(FileSource(file))
    }

    /** Use URI as the pdf source, for use with content providers  */
    fun fromUri(uri: Uri): Configurator {
        return Configurator(UriSource(uri))
    }

    /** Use bytearray as the pdf source, documents is not saved  */
    fun fromBytes(bytes: ByteArray): Configurator {
        return Configurator(ByteArraySource(bytes))
    }

    /** Use stream as the pdf source. Stream will be written to bytearray, because native code does not support Java Streams  */
    fun fromStream(stream: InputStream): Configurator {
        return Configurator(InputStreamSource(stream))
    }

    /** Use custom source as pdf source  */
    fun fromSource(docSource: DocumentSource): Configurator {
        return Configurator(docSource)
    }

    private enum class State {
        DEFAULT, LOADED, SHOWN, ERROR
    }

    companion object {
        private const val TAG = "PDFView"
    }

    inner class Configurator(private val documentSource: DocumentSource) {
        private var pageNumbers: IntArray? = null
        private var enableSwipe = true
        private var enableDoubletap = true
        private var onDrawListener: OnDrawListener? = null
        private var onDrawAllListener: OnDrawListener? = null
        private var onLoadCompleteListener: OnLoadCompleteListener? = null
        private var onErrorListener: OnErrorListener? = null
        private var onPageChangeListener: OnPageChangeListener? = null
        private var onPageScrollListener: OnPageScrollListener? = null
        private var onRenderListener: OnRenderListener? = null
        private var onTapListener: OnTapListener? = null
        private var onLongPressListener: OnLongPressListener? = null
        private var onPageErrorListener: OnPageErrorListener? = null
        private var linkHandler: LinkHandler? = DefaultLinkHandler(this@PDFView)
        private var defaultPage = 0
        private var swipeHorizontal = false
        private var annotationRendering = false
        private var password: String? = null
        private var scrollHandle: ScrollHandle? = null
        private var antialiasing = true
        private var spacing = 0
        private var autoSpacing = false
        private var pageFitPolicy = FitPolicy.WIDTH
        private var fitEachPage = false
        private var pageFling = false
        private var pageSnap = false
        private var nightMode = false
        private var textSelectionEnabled = false
        private var onTextSelectedListener: OnTextSelectedListener? = null

        fun pages(vararg pageNumbers: Int): Configurator {
            this.pageNumbers = pageNumbers
            return this
        }

        fun enableSwipe(enableSwipe: Boolean): Configurator {
            this.enableSwipe = enableSwipe
            return this
        }

        fun enableDoubletap(enableDoubletap: Boolean): Configurator {
            this.enableDoubletap = enableDoubletap
            return this
        }

        fun enableAnnotationRendering(annotationRendering: Boolean): Configurator {
            this.annotationRendering = annotationRendering
            return this
        }

        fun onDraw(onDrawListener: OnDrawListener?): Configurator {
            this.onDrawListener = onDrawListener
            return this
        }

        fun onDrawAll(onDrawAllListener: OnDrawListener?): Configurator {
            this.onDrawAllListener = onDrawAllListener
            return this
        }

        fun onLoad(onLoadCompleteListener: OnLoadCompleteListener?): Configurator {
            this.onLoadCompleteListener = onLoadCompleteListener
            return this
        }

        fun onPageScroll(onPageScrollListener: OnPageScrollListener?): Configurator {
            this.onPageScrollListener = onPageScrollListener
            return this
        }

        fun onError(onErrorListener: OnErrorListener?): Configurator {
            this.onErrorListener = onErrorListener
            return this
        }

        fun onPageError(onPageErrorListener: OnPageErrorListener?): Configurator {
            this.onPageErrorListener = onPageErrorListener
            return this
        }

        fun onPageChange(onPageChangeListener: OnPageChangeListener?): Configurator {
            this.onPageChangeListener = onPageChangeListener
            return this
        }

        fun onRender(onRenderListener: OnRenderListener?): Configurator {
            this.onRenderListener = onRenderListener
            return this
        }

        fun onTap(onTapListener: OnTapListener?): Configurator {
            this.onTapListener = onTapListener
            return this
        }

        fun onLongPress(onLongPressListener: OnLongPressListener?): Configurator {
            this.onLongPressListener = onLongPressListener
            return this
        }

        fun linkHandler(linkHandler: LinkHandler?): Configurator {
            this.linkHandler = linkHandler
            return this
        }

        fun defaultPage(defaultPage: Int): Configurator {
            this.defaultPage = defaultPage
            return this
        }

        fun swipeHorizontal(swipeHorizontal: Boolean): Configurator {
            this.swipeHorizontal = swipeHorizontal
            return this
        }

        fun password(password: String?): Configurator {
            this.password = password
            return this
        }

        fun scrollHandle(scrollHandle: ScrollHandle?): Configurator {
            this.scrollHandle = scrollHandle
            return this
        }

        fun enableAntialiasing(antialiasing: Boolean): Configurator {
            this.antialiasing = antialiasing
            return this
        }

        fun spacing(spacing: Int): Configurator {
            this.spacing = spacing
            return this
        }

        fun autoSpacing(autoSpacing: Boolean): Configurator {
            this.autoSpacing = autoSpacing
            return this
        }

        fun pageFitPolicy(pageFitPolicy: FitPolicy): Configurator {
            this.pageFitPolicy = pageFitPolicy
            return this
        }

        fun fitEachPage(fitEachPage: Boolean): Configurator {
            this.fitEachPage = fitEachPage
            return this
        }

        fun pageSnap(pageSnap: Boolean): Configurator {
            this.pageSnap = pageSnap
            return this
        }

        fun pageFling(pageFling: Boolean): Configurator {
            this.pageFling = pageFling
            return this
        }

        fun nightMode(nightMode: Boolean): Configurator {
            this.nightMode = nightMode
            return this
        }

        fun disableLongpress(): Configurator {
            this@PDFView.dragPinchManager!!.disableLongpress()
            return this
        }

        /**
         * Enable text selection functionality.
         * When enabled, long press on text will start selection with handles.
         */
        fun enableTextSelection(enabled: Boolean): Configurator {
            this.textSelectionEnabled = enabled
            return this
        }

        /**
         * Set a listener for text selection events.
         */
        fun onTextSelected(listener: OnTextSelectedListener?): Configurator {
            this.onTextSelectedListener = listener
            return this
        }

        fun load() {
            if (!hasSize) {
                waitingDocumentConfigurator = this
                return
            }
            this@PDFView.recycle()
            
            // Initialize disk cache manager
            if (this@PDFView.diskCacheManager == null) {
                this@PDFView.diskCacheManager = com.hyntix.pdf.viewer.cache.DiskCacheManager(context)
            }
            
            // Initialize bitmap pool for reduced GC pressure
            if (this@PDFView.bitmapPool == null) {
                this@PDFView.bitmapPool = com.hyntix.pdf.viewer.cache.BitmapPool()
            }
            
            // Compute document hash for cache keys
            this@PDFView.documentHash = when (documentSource) {
                is FileSource -> documentSource.file.absolutePath.hashCode().toString(16)
                is UriSource -> documentSource.uri.toString().hashCode().toString(16)
                is AssetSource -> documentSource.assetName.hashCode().toString(16)
                is ByteArraySource -> documentSource.data.contentHashCode().toString(16)
                else -> System.currentTimeMillis().toString(16)
            }
            
            this@PDFView.callbacks.onLoadComplete = onLoadCompleteListener
            this@PDFView.callbacks.onError = onErrorListener
            this@PDFView.callbacks.onDraw = onDrawListener
            this@PDFView.callbacks.onDrawAll = onDrawAllListener
            this@PDFView.callbacks.onPageChange = onPageChangeListener
            this@PDFView.callbacks.onPageScroll = onPageScrollListener
            this@PDFView.callbacks.onRender = onRenderListener
            this@PDFView.callbacks.onTap = onTapListener
            this@PDFView.callbacks.onLongPress = onLongPressListener
            this@PDFView.callbacks.onPageError = onPageErrorListener
            this@PDFView.callbacks.linkHandler = linkHandler
            this@PDFView.setSwipeEnabled(enableSwipe)
            this@PDFView.setNightMode(nightMode)
            this@PDFView.enableDoubletap(enableDoubletap)
            this@PDFView.setDefaultPage(defaultPage)
            this@PDFView.setSwipeVertical(!swipeHorizontal)
            this@PDFView.enableAnnotationRendering(annotationRendering)
            this@PDFView.setScrollHandle(scrollHandle)
            this@PDFView.enableAntialiasing(antialiasing)
            this@PDFView.setSpacing(spacing)
            this@PDFView.setAutoSpacing(autoSpacing)
            this@PDFView.setPageFitPolicy(pageFitPolicy)
            this@PDFView.setFitEachPage(fitEachPage)
            this@PDFView.setPageSnap(pageSnap)
            this@PDFView.setPageFling(pageFling)
            this@PDFView.setTextSelectionEnabled(textSelectionEnabled)
            this@PDFView.callbacks.onTextSelected = onTextSelectedListener
            if (pageNumbers != null) {
                this@PDFView.load(documentSource, password, pageNumbers)
            } else {
                this@PDFView.load(documentSource, password)
            }
        }
    }

    companion object {
        private val TAG = PDFView::class.java.simpleName
        const val DEFAULT_MAX_SCALE = 10.0f
        const val DEFAULT_MID_SCALE = 2.0f
        const val DEFAULT_MID_SCALE_2 = 5.0f
        const val DEFAULT_MID_SCALE_3 = 10.0f
        const val DEFAULT_MIN_SCALE = 1.0f
    }
    /**
     * Converts screen coordinates to Page Index and PDF Point.
     * Uses PDFium's device-to-page coordinate transformation.
     * @param x Screen X
     * @param y Screen Y
     * @return Pair(PageIndex, PointF(pdfX, pdfY)) or null
     */
    fun getPagePoint(x: Float, y: Float): androidx.core.util.Pair<Int, PointF>? {
        val pdfFile = this.pdfFile ?: return null
        val mappedX = -currentXOffset + x
        val mappedY = -currentYOffset + y
        val page = pdfFile.getPageAtOffset(if (isSwipeVertical) mappedY else mappedX, zoom)
        
        val pageSize = pdfFile.getScaledPageSize(page, zoom)
        val pageOffsetX: Float
        val pageOffsetY: Float
        if (isSwipeVertical) {
            pageOffsetX = pdfFile.getSecondaryPageOffset(page, zoom)
            pageOffsetY = pdfFile.getPageOffset(page, zoom)
        } else {
            pageOffsetY = pdfFile.getSecondaryPageOffset(page, zoom)
            pageOffsetX = pdfFile.getPageOffset(page, zoom)
        }
        
        // Local coordinates within the rendered page bitmap (scaled)
        val localX = (mappedX - pageOffsetX).toInt()
        val localY = (mappedY - pageOffsetY).toInt()
        
        // Use PDFium's native coordinate mapping (handles rotation, Y-flip, etc.)
        val pdfCoords = pdfFile.mapDeviceToPage(
            page,
            0, 0, // startX, startY of the rendered area
            pageSize.width.toInt(), pageSize.height.toInt(), // size of rendered area
            localX, localY
        )
        
        return androidx.core.util.Pair(page, PointF(pdfCoords[0].toFloat(), pdfCoords[1].toFloat()))
    }
}

