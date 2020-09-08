package com.maplewing

import org.jetbrains.exposed.sql.Table

data class TestCase(
    val id: String,
    val input: String,
    val expectedOutput: String,
    val comment: String,
    val score: Int,
    val timeOutSeconds: Double
)

data class TestCasePostDTO(
    val input: String,
    val expectedOutput: String,
    val comment: String,
    val score: Int,
    val timeOutSeconds: Double
)

data class TestCasePutDTO(
    val id: String?,
    val input: String,
    val expectedOutput: String,
    val comment: String,
    val score: Int,
    val timeOutSeconds: Double
)

object TestCaseTable : Table() {
    val id = integer("TestCaseId").autoIncrement().primaryKey()
    val input = text("TestInput")
    val expectedOutput = text("ExpectedOutput")
    val comment = text("Comment")
    val score = integer("Score")
    val timeOutSeconds = double("TimeOutSeconds")

    val problemId = integer("ProblemId") references ProblemTable.id
}