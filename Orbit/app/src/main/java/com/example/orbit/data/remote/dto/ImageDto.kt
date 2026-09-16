package com.example.orbit.data.remote.dto

import kotlinx.serialization.Serializable

/** F-37: putanja pod kojom je server sacuvao sliku */
@Serializable
data class ImageUploadDto(val path: String)
