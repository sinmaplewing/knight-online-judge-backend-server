package com.maplewing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.html.*
import kotlinx.html.*
import kotlinx.css.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.features.*
import io.ktor.http.content.TextContent
import io.ktor.jackson.JacksonConverter
import io.ktor.jackson.jackson
import io.ktor.util.pipeline.PipelineContext
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.NullPointerException
import java.util.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun initDatabase() {
    val config = HikariConfig("/hikari.properties")
    config.schema = "public"
    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)

    transaction {
        SchemaUtils.create(ProblemTable, TestCaseTable)
    }
}

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    initDatabase()

    val client = HttpClient(Apache) {
    }

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT) // Pretty Prints the JSON
        }
    }

    install(StatusPages) {
        exception<Throwable> {
            call.respond(HttpStatusCode.InternalServerError)
        }

        exception<com.fasterxml.jackson.core.JsonParseException> {
            call.respond(HttpStatusCode.BadRequest)
        }

        exception<com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException> {
            call.respond(HttpStatusCode.BadRequest)
        }

        exception<NotFoundException> {
            call.respond(HttpStatusCode.NotFound)
        }

        exception<IdAlreadyExistedException> {
            call.respond(HttpStatusCode.BadRequest)
        }
    }

    routing {
        get("/") {
            call.respond(mapOf("OK" to true))
        }

        route("/problems") {
            get {
                var problems: List<Map<String, Any>>? = null

                transaction {
                    problems = ProblemTable.selectAll().map {
                        mapOf(
                            "id" to it[ProblemTable.id].toString(),
                            "title" to it[ProblemTable.title]
                        )
                    }
                }

                call.respond(mapOf(
                    "data" to problems
                ))
            }

            post {
                val newProblem = call.receive<ProblemPostDTO>()
                var newProblemId : Int? = null

                transaction {
                    newProblemId = ProblemTable.insert {
                        it[title] = newProblem.title
                        it[description] = newProblem.description
                    } get ProblemTable.id

                    for (testCase in newProblem.testCases) {
                        TestCaseTable.insert {
                            it[input] = testCase.input
                            it[expectedOutput] = testCase.expectedOutput
                            it[comment] = testCase.comment
                            it[score] = testCase.score
                            it[timeOutSeconds] = testCase.timeOutSeconds
                            it[problemId] = newProblemId!!
                        }
                    }
                }

                call.respond(mapOf(
                    "problem_id" to newProblemId
                ))
            }

            route("/{id}") {
                get {
                    val requestId = call.parameters["id"]?.toInt() ?:
                        throw BadRequestException("The type of Id is wrong.")
                    var responseData: Problem? = null

                    transaction {
                        val requestProblem = ProblemTable.select {
                            ProblemTable.id.eq(requestId)
                        }.first()

                        val requestTestCases = TestCaseTable.select {
                            TestCaseTable.problemId.eq(requestId)
                        }.map {
                            TestCase(
                                id = it[TestCaseTable.id].toString(),
                                input = it[TestCaseTable.input],
                                expectedOutput = it[TestCaseTable.expectedOutput],
                                comment = it[TestCaseTable.comment],
                                score = it[TestCaseTable.score],
                                timeOutSeconds = it[TestCaseTable.timeOutSeconds]
                            )
                        }.toList()

                        responseData = Problem(
                            id = requestProblem[ProblemTable.id].toString(),
                            title = requestProblem[ProblemTable.title],
                            description = requestProblem[ProblemTable.description],
                            testCases = requestTestCases
                        )
                    }

                    call.respond(mapOf("data" to responseData))
                }

                put {
                    val requestId = call.parameters["id"]?.toInt() ?:
                        throw BadRequestException("The type of Id is wrong.")
                    val updateProblemContent = call.receive<ProblemPutDTO>()

                    transaction {
                        ProblemTable.update({ ProblemTable.id.eq(requestId)}) {
                            it[ProblemTable.title] = updateProblemContent.title
                            it[ProblemTable.description] = updateProblemContent.description
                        }

                        TestCaseTable.deleteWhere {
                            TestCaseTable.problemId.eq(requestId)
                                    .and(TestCaseTable.id.notInList(
                                            updateProblemContent.testCases
                                                    .mapNotNull { it.id?.toInt() }
                                    ))
                        }

                        for (testcase in updateProblemContent.testCases) {
                            if (testcase.id == null) {
                                TestCaseTable.insert {
                                    it[TestCaseTable.input] = testcase.input
                                    it[TestCaseTable.expectedOutput] = testcase.expectedOutput
                                    it[TestCaseTable.comment] = testcase.comment
                                    it[TestCaseTable.score] = testcase.score
                                    it[TestCaseTable.timeOutSeconds] = testcase.timeOutSeconds
                                    it[TestCaseTable.problemId] = requestId
                                }
                                continue
                            }

                            TestCaseTable.update({ TestCaseTable.id.eq(testcase.id.toInt()) }){
                                it[TestCaseTable.input] = testcase.input
                                it[TestCaseTable.expectedOutput] = testcase.expectedOutput
                                it[TestCaseTable.comment] = testcase.comment
                                it[TestCaseTable.score] = testcase.score
                                it[TestCaseTable.timeOutSeconds] = testcase.timeOutSeconds
                            }
                        }
                    }

                    call.respond(mapOf(
                        "OK" to true
                    ))
                }

                delete {
                    val requestId = call.parameters["id"]?.toInt() ?:
                        throw BadRequestException("The type of Id is wrong.")

                    transaction {
                        TestCaseTable.deleteWhere { TestCaseTable.problemId.eq(requestId) }
                        ProblemTable.deleteWhere { ProblemTable.id.eq(requestId) }
                    }

                    call.respond(mapOf(
                        "OK" to true
                    ))
                }
            }
        }
    }
}
