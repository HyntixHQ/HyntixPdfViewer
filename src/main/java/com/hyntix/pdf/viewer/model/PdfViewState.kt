package com.hyntix.pdf.viewer.model

import android.os.Parcel
import android.os.Parcelable

/**
 * Represents the state of the PDF viewer that can be saved and restored.
 * Use this with savedInstanceState or other persistence mechanisms.
 * 
 * @param currentPage The current page index (0-indexed)
 * @param zoom The current zoom level
 * @param offsetX The current X scroll offset
 * @param offsetY The current Y scroll offset
 */
data class PdfViewState(
    val currentPage: Int = 0,
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(currentPage)
        parcel.writeFloat(zoom)
        parcel.writeFloat(offsetX)
        parcel.writeFloat(offsetY)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<PdfViewState> {
        override fun createFromParcel(parcel: Parcel): PdfViewState {
            return PdfViewState(parcel)
        }

        override fun newArray(size: Int): Array<PdfViewState?> {
            return arrayOfNulls(size)
        }
    }
}
