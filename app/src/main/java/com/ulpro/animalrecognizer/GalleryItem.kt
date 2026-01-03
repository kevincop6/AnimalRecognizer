package com.ulpro.animalrecognizer

import android.os.Parcel
import android.os.Parcelable

data class GalleryItem(
    val id: Int,
    val titulo: String,
    val imageUrl: String,
    val descripcion: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString().orEmpty(),
        parcel.readString().orEmpty(),
        parcel.readString().orEmpty()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(titulo)
        parcel.writeString(imageUrl)
        parcel.writeString(descripcion)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<GalleryItem> =
            object : Parcelable.Creator<GalleryItem> {
                override fun createFromParcel(parcel: Parcel) = GalleryItem(parcel)
                override fun newArray(size: Int): Array<GalleryItem?> = arrayOfNulls(size)
            }
    }
}
