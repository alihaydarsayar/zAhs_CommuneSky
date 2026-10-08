package com.alihaydarsayar.communesky.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Kullanıcının eklediği yer. "Bulunduğum yer" bu tabloda değildir; o hep listenin başındadır. */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "Atakum, Samsun, Türkiye" gibi ayırt edici açıklama. */
    val region: String?,
    val latitude: Double,
    val longitude: Double,
    /** Listede ve ana ekranda kaçıncı sırada; küçükten büyüğe. */
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "0") val isHome: Boolean = false,
)
