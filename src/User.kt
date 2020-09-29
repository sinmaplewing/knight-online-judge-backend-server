package com.maplewing

import org.jetbrains.exposed.sql.Table

data class User (
    val id: Int,
    val username: String,
    val password: String,
    val name: String,
    val email: String,
    val authority: Int
)

data class UserPostDTO (
    val username: String,
    val password: String,
    val name: String,
    val email: String
)

data class UserLoginDTO (
    val username: String,
    val password: String
)

data class UserCheckDTO (
    val userId: Int? = null,
    val name: String = "",
    val authority: Int = 0
)

object UserTable: Table() {
    val id = integer("UserId").autoIncrement().primaryKey()
    val username = varchar("Username", 255).uniqueIndex()
    val password = varchar("Password", 255)
    val name = varchar("Name", 255)
    val email = varchar("Email", 255)
    val authority = integer("Authority")
}