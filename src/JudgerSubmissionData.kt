package com.maplewing

data class JudgerSubmissionData(
    val id: Int,
    val langauge: String,
    val code: String,
    val testCases: List<JudgerTestCaseData>
)

data class JudgerTestCaseData(
    val input: String,
    val expectedOutput: String,
    val score: Int,
    val timeOutSeconds: Double
)