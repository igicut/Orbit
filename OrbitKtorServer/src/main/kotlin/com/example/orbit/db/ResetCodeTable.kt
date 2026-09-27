package com.example.orbit.db

import org.jetbrains.exposed.v1.core.Table

/** F-13: jednokratni kod za novu lozinku, jedan po emailu; cuva se samo bcrypt hash koda */
object PasswordResetCodes : Table("password_reset_codes") {
    val email = varchar("email", 254)
    val codeHash = varchar("code_hash", 60)
    val expiresAt = long("expires_at")

    /** Koliko puta je kod proveravan; posle pet provera vise ne vazi */
    val attempts = integer("attempts").default(0)

    override val primaryKey = PrimaryKey(email)
}
