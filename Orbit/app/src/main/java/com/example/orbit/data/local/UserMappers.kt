package com.example.orbit.data.local

import com.example.orbit.data.local.entity.UserEntity
import com.example.orbit.domain.model.User

fun UserEntity.toDomain(): User = User(
    id = id,
    displayName = displayName,
    interests = interests,
)

fun User.toEntity(): UserEntity = UserEntity(
    id = id,
    displayName = displayName,
    interests = interests,
)
