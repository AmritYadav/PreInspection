package com.tataaig.preinspection.main.model

import android.graphics.Bitmap

data class Inspection(
    val title: String,
    val thumbnail: Bitmap? = null,
    val shots: Int = 0
)