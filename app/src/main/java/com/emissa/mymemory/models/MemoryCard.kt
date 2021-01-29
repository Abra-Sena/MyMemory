package com.emissa.mymemory.models

import android.service.carrier.CarrierIdentifier

data class MemoryCard (
    val identifier: Int,
    val imageUrl: String? = null, // this is optional
    var isFaceUp: Boolean = false,
    var isMatched: Boolean = false
)