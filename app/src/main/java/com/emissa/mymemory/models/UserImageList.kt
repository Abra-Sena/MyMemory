package com.emissa.mymemory.models

import com.google.firebase.firestore.PropertyName
import com.google.j2objc.annotations.Property

data class UserImageList (
    @PropertyName("images") val images: List<String>? = null
)
