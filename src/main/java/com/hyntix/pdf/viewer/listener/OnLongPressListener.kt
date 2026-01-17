package com.hyntix.pdf.viewer.listener

import android.view.MotionEvent

interface OnLongPressListener {
    /**
     * Called when the user has a long tap gesture
     *
     * @param e MotionEvent that registered as a confirmed long tap
     */
    fun onLongPress(e: MotionEvent)
}
