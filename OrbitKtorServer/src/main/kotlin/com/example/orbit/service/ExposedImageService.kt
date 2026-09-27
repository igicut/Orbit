package com.example.orbit.service

import com.example.orbit.db.Images
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/** Pristup bazi za vlasnistvo nad slikama */
class ExposedImageService(private val database: R2dbcDatabase) {

    /** path je ono sto je vratio POST /images, npr. /images/<uuid>.jpg */
    suspend fun recordUpload(path: String, uploaderId: String) {
        suspendTransaction(database) {
            Images.insert {
                it[name] = path.removePrefix(IMAGE_PATH_PREFIX)
                it[Images.uploaderId] = uploaderId
                it[createdAt] = System.currentTimeMillis()
            }
        }
    }

    /** null za sliku bez reda, na primer iz seed podataka */
    suspend fun uploaderOf(path: String): String? = suspendTransaction(database) {
        Images.select(Images.uploaderId)
            .where { Images.name eq path.removePrefix(IMAGE_PATH_PREFIX) }
            .map { it[Images.uploaderId] }
            .singleOrNull()
    }
}
