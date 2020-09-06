package com.maplewing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import java.util.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

class PlainConverter() : ContentConverter {
    override suspend fun convertForSend(context: PipelineContext<Any, ApplicationCall>, contentType: ContentType, value: Any): Any? {
        return TextContent("XD", contentType.withCharset(context.call.suitableCharset()))
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        return null
    }
}

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    val testProblems = Collections.synchronizedList(mutableListOf(
        Problem(
            "101",
            "A + B Problem",
            "輸入兩數，將兩數加總。",
            listOf(
                TestCase(
                    "3 4",
                    "7",
                    "",
                    50,
                    10.0
                ),
                TestCase(
                    "2147483646 1",
                    "2147483647",
                    "",
                    50,
                    10.0
                )
            )
        ),
        Problem(
            "102",
            "A + B + C Problem",
            "輸入三數，將三數加總。",
            listOf(
                TestCase(
                    "3 4 5",
                    "12",
                    "",
                    50,
                    10.0
                ),
                TestCase(
                    "2147483646 1 -1",
                    "2147483646",
                    "",
                    50,
                    10.0
                )
            )
        )
    ))


    val client = HttpClient(Apache) {
    }

     // Pretty Prints the JSON

    install(ContentNegotiation) {

        register(ContentType.Text.Plain, PlainConverter())

        register(ContentType.Application.Json, JacksonConverter(
            jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        ))

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
                val problems = testProblems.map {
                    mapOf(
                        "id" to it.id,
                        "title" to it.title
                    )
                }

                call.respond(mapOf(
                    "data" to problems
                ))
            }

            post {
                val newProblem = call.receive<Problem>()
                if (testProblems.any { it.id == newProblem.id }) {
                    throw IdAlreadyExistedException()
                }

                testProblems += newProblem

                call.respond(mapOf(
                    "OK" to true
                ))
            }

            route("/{id}") {
                get {
                    val requestId = call.parameters["id"]
                    val requestProblem = testProblems.firstOrNull() {
                        it.id == requestId
                    };

                    call.respond(
                        mapOf(
                            "problem" to (requestProblem ?: throw NotFoundException())
                        )
                    )
                }

                put {
                    val requestId = call.parameters["id"]
                    if (!testProblems.removeIf { it.id == requestId }) {
                        throw NotFoundException()
                    }

                    val updateProblemContent = call.receive<Problem>()
                    testProblems += updateProblemContent
                    call.respond(mapOf(
                        "OK" to true
                    ))
                }

                delete {
                    val requestId = call.parameters["id"]
                    if (!testProblems.removeIf { it.id == requestId }) {
                        throw NotFoundException()
                    }

                    call.respond(mapOf(
                        "OK" to true
                    ))
                }
            }
        }
    }
}
