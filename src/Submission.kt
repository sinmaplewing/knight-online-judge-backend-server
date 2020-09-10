package com.maplewing

import org.jetbrains.exposed.sql.Table

object SubmissionTable: Table() {
    val id = integer("SubmissionId").autoIncrement().primaryKey()
    val language = varchar("Language", 255)
    val code = text("Code")
    val executedTime = double("ExecutedTime")
    val result = varchar("Result", 255)

    val problemId = integer("ProblemId") references ProblemTable.id
    val userId = integer("UserId") references UserTable.id
}

data class Submission(
    val id: Int,
    val language: String,
    val code: String,
    val executedTime: Double,
    val result: String,
    val problemId: Int,
    val userId: Int
)

data class SubmissionPostDTO(
    val language: String,
    val code: String,
    val problemId: Int
)