package com.maplewing

import at.favre.lib.crypto.bcrypt.BCrypt

const val BCRYPT_COST = 12

object PasswordHasher {
    fun hashPassword(password: String) =
        BCrypt.withDefaults().hashToString(
            BCRYPT_COST,
            password.toCharArray()
        )

    fun verifyPassword(password: String, passwordHash: String) =
        BCrypt.verifyer().verify(
            password.toCharArray(),
            passwordHash
        ).verified
}