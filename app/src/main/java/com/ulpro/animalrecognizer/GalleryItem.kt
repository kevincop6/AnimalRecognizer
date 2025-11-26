package com.ulpro.animalrecognizer


import android.os.Parcel
import android.os.Parcelable

data class GalleryItem(
    val id: Int,
    val name: String,
    val imageBase64: String,
    val descripcion: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(name)
        parcel.writeString(imageBase64)
        parcel.writeString(descripcion)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<GalleryItem> {
        override fun createFromParcel(parcel: Parcel): GalleryItem {
            return GalleryItem(parcel)
        }

        override fun newArray(size: Int): Array<GalleryItem?> {
            return arrayOfNulls(size)
        }
    }
}