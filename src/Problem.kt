package com.maplewing

import org.jetbrains.exposed.sql.*

data class Problem(
    val id: String,
    val title: String,
    val description: String,
    val testCases: List<TestCase>
)

data class ProblemDetailData(
    val id: String,
    val title: String,
    val description: String
)

data class ProblemPostDTO(
    val title: String,
    val description: String,
    val testCases: List<TestCasePostDTO>
)

data class ProblemPutDTO(
    val id: String,
    val title: String,
    val description: String,
    val testCases: List<TestCasePutDTO>
)

object ProblemTable : Table() {
    val id = integer("ProblemId").autoIncrement().primaryKey()
    val title = text("Title")
    val description = text("Description")
}
