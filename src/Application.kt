package com.maplewing

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.features.*
import io.ktor.jackson.jackson
import io.ktor.sessions.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import redis.clients.jedis.Jedis

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

const val SESSION_LOGIN_DATA_NAME = "login_data"
const val NORMAL_USER_AUTHENTICAION_NAME = "Normal User"
const val SUPER_USER_AUTHENTICATION_NAME = "Super User"
const val SUBMISSION_NO_RESULT = "-"

fun initDatabase() {
    val config = HikariConfig("/hikari.properties")
    config.schema = "public"
    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)

    transaction {
        SchemaUtils.create(ProblemTable, TestCaseTable, UserTable, SubmissionTable)
    }
}

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    initDatabase()

    var jedis: Jedis? = Jedis()

    val client = HttpClient(Apache) {
    }

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT) // Pretty Prints the JSON
        }
    }

    install(Sessions) {
        cookie<UserIdAuthorityPrincipal>(
            SESSION_LOGIN_DATA_NAME,
            storage = SessionStorageMemory()
        ) {
            cookie.path = "/"
            cookie.extensions["SameSite"] = "None"
            cookie.extensions["Secure"] = "true"
        }
    }

    install(Authentication) {
        session<UserIdAuthorityPrincipal>(NORMAL_USER_AUTHENTICAION_NAME) {
            challenge {
                throw UnauthorizedException()
            }
            validate { session: UserIdAuthorityPrincipal ->
                session
            }
        }

        session<UserIdAuthorityPrincipal>(SUPER_USER_AUTHENTICATION_NAME) {
            challenge {
                throw UnauthorizedException()
            }
            validate { session: UserIdAuthorityPrincipal ->
                if (session.authority.toInt() > 1) session else null
            }
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

        exception<BadRequestException> {
            call.respond(HttpStatusCode.BadRequest)
        }

        exception<UnauthorizedException> {
            call.respond(HttpStatusCode.Unauthorized)
        }
    }

    install(CORS) {
        method(HttpMethod.Get)
        method(HttpMethod.Post)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Options)
        anyHost()

        allowCredentials = true
        allowNonSimpleContentTypes = true
    }

    routing {
        get("/") {
            call.respond(mapOf("OK" to true))
        }

        route("/problems") {
            authenticate(NORMAL_USER_AUTHENTICAION_NAME, optional = true) {
                get {
                    val userIdAuthorityPrincipal = call.sessions.get<UserIdAuthorityPrincipal>()
                    var problems: List<Map<String, Any>>? = null

                    transaction {
                        val problemContents = ProblemTable.selectAll()
                            .orderBy(ProblemTable.id).map {
                                mutableMapOf(
                                    "id" to it[ProblemTable.id].toString(),
                                    "title" to it[ProblemTable.title]
                                )
                            }

                        if (userIdAuthorityPrincipal == null) {
                            problems = problemContents
                        } else {
                            val problemIds = problemContents.mapNotNull { it?.get("id")?.toInt() }
                            val minProblemId = problemIds.min()
                            val maxProblemId = problemIds.max()

                            if (minProblemId != null && maxProblemId != null) {
                                val distinctIdCount = SubmissionTable.id.countDistinct()
                                val acceptedResultSum = SubmissionTable.result.like("Accepted%")
                                    .castTo<Int>(IntegerColumnType())
                                    .sum()

                                val submissions = SubmissionTable
                                    .slice(
                                        SubmissionTable.problemId,
                                        distinctIdCount,
                                        acceptedResultSum
                                    ).select {
                                        SubmissionTable.problemId.lessEq(maxProblemId).and(
                                            SubmissionTable.problemId.greaterEq(minProblemId)
                                    )}.groupBy(SubmissionTable.problemId)
                                    .forEach { row ->
                                        val problemElement = problemContents.first {
                                            it?.get("id") == row[SubmissionTable.problemId].toString()
                                        }
                                        val acceptedResultSum = row[acceptedResultSum]
                                        problemElement["isSubmitted"] = (row[distinctIdCount] > 0).toString()
                                        problemElement["isAccepted"] = (acceptedResultSum != null && acceptedResultSum > 0).toString()
                                    }
                            }
                            problems = problemContents
                        }
                    }

                    call.respond(
                        mapOf(
                            "data" to problems,
                            "isEditable" to ((userIdAuthorityPrincipal?.authority?.toInt() ?: 0) > 1)
                        )
                    )
                }

                get {
                    val userIdAuthorityPrincipal = call.sessions.get<UserIdAuthorityPrincipal>()
                    var problems: List<Map<String, Any>>? = null

                    transaction {
                        val problemContents = ProblemTable.selectAll().map {
                            mutableMapOf(
                                "id" to it[ProblemTable.id].toString(),
                                "title" to it[ProblemTable.title]
                            )
                        }

                        if (userIdAuthorityPrincipal == null) {
                            problems = problemContents
                        } else {
                            val problemIds = problemContents.mapNotNull { it?.get("id")?.toInt() }
                            val minProblemId = problemIds.min()
                            val maxProblemId = problemIds.max()

                            if (minProblemId != null && maxProblemId != null) {
                                val distinctIdCount = SubmissionTable.id.countDistinct()
                                val acceptedResultSum = SubmissionTable.result.like("Accepted%")
                                    .castTo<Int>(IntegerColumnType())
                                    .sum()

                                val submissions = SubmissionTable
                                    .slice(
                                        SubmissionTable.problemId,
                                        distinctIdCount,
                                        acceptedResultSum
                                    ).select {
                                        SubmissionTable.problemId.lessEq(maxProblemId).and(
                                            SubmissionTable.problemId.greaterEq(minProblemId)
                                        )}.groupBy(SubmissionTable.problemId)
                                    .forEach { row ->
                                        val problemElement = problemContents.first {
                                            it?.get("id") == row[SubmissionTable.problemId].toString()
                                        }
                                        val acceptedResultSum = row[acceptedResultSum]
                                        problemElement["isSubmitted"] = (row[distinctIdCount] > 0).toString()
                                        problemElement["isAccepted"] = (acceptedResultSum != null && acceptedResultSum > 0).toString()
                                    }
                            }
                            problems = problemContents
                        }
                    }

                    call.respond(
                        mapOf(
                            "data" to problems
                        )
                    )
                }
            }

            authenticate(SUPER_USER_AUTHENTICATION_NAME) {
                post {
                    val newProblem = call.receive<ProblemPostDTO>()
                    var newProblemId: Int? = null

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

                    call.respond(
                        mapOf(
                            "problem_id" to newProblemId
                        )
                    )
                }
            }

            route("/{id}") {
                get {
                    val requestId = call.parameters["id"]?.toInt() ?:
                        throw BadRequestException("The type of Id is wrong.")
                    var responseData: ProblemDetailData? = null

                    transaction {
                        val requestProblem = ProblemTable.select {
                            ProblemTable.id.eq(requestId)
                        }.first()

                        responseData = ProblemDetailData(
                            id = requestProblem[ProblemTable.id].toString(),
                            title = requestProblem[ProblemTable.title],
                            description = requestProblem[ProblemTable.description]
                        )
                    }

                    call.respond(mapOf("data" to responseData))
                }

                authenticate(SUPER_USER_AUTHENTICATION_NAME) {
                    get("/all") {
                        val requestId =
                            call.parameters["id"]?.toInt() ?: throw BadRequestException("The type of Id is wrong.")
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
                }

                authenticate(SUPER_USER_AUTHENTICATION_NAME) {
                    put {
                        val requestId =
                            call.parameters["id"]?.toInt() ?: throw BadRequestException("The type of Id is wrong.")
                        val updateProblemContent = call.receive<ProblemPutDTO>()

                        transaction {
                            ProblemTable.update({ ProblemTable.id.eq(requestId) }) {
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

                                TestCaseTable.update({ TestCaseTable.id.eq(testcase.id.toInt()) }) {
                                    it[TestCaseTable.input] = testcase.input
                                    it[TestCaseTable.expectedOutput] = testcase.expectedOutput
                                    it[TestCaseTable.comment] = testcase.comment
                                    it[TestCaseTable.score] = testcase.score
                                    it[TestCaseTable.timeOutSeconds] = testcase.timeOutSeconds
                                }
                            }
                        }

                        call.respond(
                            mapOf(
                                "OK" to true
                            )
                        )
                    }

                    delete {
                        val requestId =
                            call.parameters["id"]?.toInt() ?: throw BadRequestException("The type of Id is wrong.")

                        transaction {
                            TestCaseTable.deleteWhere { TestCaseTable.problemId.eq(requestId) }
                            ProblemTable.deleteWhere { ProblemTable.id.eq(requestId) }
                        }

                        call.respond(
                            mapOf(
                                "OK" to true
                            )
                        )
                    }
                }
            }
        }

        route("/users") {
            get {
                var users: List<Map<String, Any>>? = null

                transaction {
                    val userContents = UserTable.selectAll().map {
                        mutableMapOf(
                            "id" to it[UserTable.id].toString(),
                            "name" to it[UserTable.name]
                        )
                    }

                    val solvedProblemCount = mutableMapOf<Int, Int>()
                    val acPairs = SubmissionTable
                        .slice(SubmissionTable.userId, SubmissionTable.problemId)
                        .select {
                            SubmissionTable.result.like("Accepted%")
                        }.groupBy(SubmissionTable.userId, SubmissionTable.problemId)
                        .forEach {
                            val userId = it[SubmissionTable.userId]
                            solvedProblemCount[userId] = solvedProblemCount.getOrDefault(userId, 0) + 1
                        }

                    for (userContent in userContents) {
                        val userContentId = userContent["id"]
                        if (userContentId != null) {
                            userContent["solvedProblemCount"] = solvedProblemCount.getOrDefault(
                                userContentId.toInt(),
                                0
                            ).toString()
                        }
                    }

                    users = userContents
                }

                call.respond(
                    mapOf(
                        "data" to users
                    )
                )
            }

            post {
                val userData = call.receive<UserPostDTO>()
                var userId: Int? = null

                transaction {
                    userId = UserTable.insert {
                        it[UserTable.username] = userData.username
                        it[UserTable.password] =
                            PasswordHasher.hashPassword(userData.password)
                        it[UserTable.name] = userData.name
                        it[UserTable.email] = userData.email
                        it[UserTable.authority] = 1 // Not used in today's example
                    } get UserTable.id
                }
                call.respond(mapOf("user_id" to userId))
            }

            post("/login") {
                try {
                    val userLoginDTO = call.receive<UserLoginDTO>()
                    var userId: Int? = null
                    var authority: Int? = null

                    transaction {
                        val userData = UserTable.select { UserTable.username.eq(userLoginDTO.username) }.firstOrNull()

                        if (userData == null) throw UnauthorizedException()

                        if (!PasswordHasher.verifyPassword(
                                userLoginDTO.password,
                                userData?.get(UserTable.password)
                            )
                        ) {
                            throw UnauthorizedException()
                        }

                        userId = userData.get(UserTable.id)
                        authority = userData.get(UserTable.authority)
                    }

                    if (userId == null || authority == null) throw UnauthorizedException()

                    call.sessions.set(
                        SESSION_LOGIN_DATA_NAME,
                        UserIdAuthorityPrincipal(userId.toString(), authority.toString())
                    )

                    call.respond(mapOf("OK" to true))
                } catch (e: Exception) {
                    call.respond(mapOf("OK" to false))
                }
            }

            post("/logout") {
                call.sessions.clear(SESSION_LOGIN_DATA_NAME)
                call.respond(mapOf("OK" to true))
            }

            authenticate(NORMAL_USER_AUTHENTICAION_NAME, optional = true) {
                get("/check") {
                    val userIdAuthorityPrincipal = call.sessions.get<UserIdAuthorityPrincipal>()

                    if (userIdAuthorityPrincipal == null) {
                        call.respond(UserCheckDTO(null))
                    } else {
                        val userId = userIdAuthorityPrincipal.userId.toInt()
                        var authority = userIdAuthorityPrincipal.authority.toInt()
                        var name = ""

                        transaction {
                            val userData = UserTable.select { UserTable.id.eq(userId) }.first()
                            name = userData[UserTable.name]
                            authority = userData[UserTable.authority]
                            call.sessions.set(
                                SESSION_LOGIN_DATA_NAME,
                                UserIdAuthorityPrincipal(userId.toString(), authority.toString())
                            )
                        }

                        call.respond(UserCheckDTO(userId, name, authority))
                    }
                }
            }
        }

        route("/submissions") {
            authenticate(NORMAL_USER_AUTHENTICAION_NAME, optional = true) {
                get {
                    var userIdAuthorityPrincipal = call.sessions.get<UserIdAuthorityPrincipal>()
                    var submissions: List<Map<String, Any>>? = null

                    transaction {
                        submissions = (SubmissionTable innerJoin ProblemTable innerJoin UserTable)
                            .slice(
                                SubmissionTable.id,
                                UserTable.id,
                                UserTable.name,
                                ProblemTable.id,
                                ProblemTable.title,
                                SubmissionTable.language,
                                SubmissionTable.result,
                                SubmissionTable.executedTime
                            ).selectAll()
                            .orderBy(SubmissionTable.id, SortOrder.DESC)
                            .map {
                                mapOf(
                                    "id" to it[SubmissionTable.id].toString(),
                                    "name" to it[UserTable.name],
                                    "problemId" to it[ProblemTable.id],
                                    "title" to it[ProblemTable.title],
                                    "language" to it[SubmissionTable.language],
                                    "result" to it[SubmissionTable.result],
                                    "executedTime" to it[SubmissionTable.executedTime],
                                    "isRefreshable" to (userIdAuthorityPrincipal != null &&
                                        it[UserTable.id].toString() == userIdAuthorityPrincipal.userId)
                                )
                            }
                    }

                    call.respond(
                        mapOf(
                            "data" to submissions,
                            "isRefreshable" to (userIdAuthorityPrincipal != null && userIdAuthorityPrincipal.authority.toInt() > 1)
                        )
                    )
                }
            }

            authenticate(NORMAL_USER_AUTHENTICAION_NAME) {
                post {
                    val submissionData = call.receive<SubmissionPostDTO>()
                    val userIdAuthorityPrincipal = call.sessions.get<UserIdAuthorityPrincipal>()
                    val userId = userIdAuthorityPrincipal?.userId
                    var submissionId: Int? = null
                    var testCaseData: List<JudgerTestCaseData>? = null

                    if (userId == null) throw UnauthorizedException()

                    transaction {
                        submissionId = SubmissionTable.insert {
                            it[SubmissionTable.language] = submissionData.language
                            it[SubmissionTable.code] = submissionData.code
                            it[SubmissionTable.executedTime] = -1.0
                            it[SubmissionTable.result] = SUBMISSION_NO_RESULT
                            it[SubmissionTable.problemId] = submissionData.problemId
                            it[SubmissionTable.userId] = userId.toInt()
                        } get SubmissionTable.id

                        testCaseData = TestCaseTable.select {
                            TestCaseTable.problemId.eq(submissionData.problemId)
                        }.map {
                            JudgerTestCaseData(
                                it[TestCaseTable.input],
                                it[TestCaseTable.expectedOutput],
                                it[TestCaseTable.score],
                                it[TestCaseTable.timeOutSeconds]
                            )
                        }.toList()
                    }

                    SubmissionTable.all

                    val judgerSubmissionId: Int? = submissionId
                    val judgerTestCaseData: List<JudgerTestCaseData>? = testCaseData
                    if (judgerSubmissionId != null && judgerTestCaseData != null) {
                        val judgerSubmissionData = JudgerSubmissionData(
                            judgerSubmissionId,
                            submissionData.language,
                            submissionData.code,
                            judgerTestCaseData
                        )

                        try {
                            jedis = jedis.getConnection()
                            val currentJedisConnection: Jedis = jedis!!
                            currentJedisConnection.rpush(
                                submissionData.language,
                                jacksonObjectMapper().writeValueAsString(judgerSubmissionData)
                            )
                            currentJedisConnection.disconnect()
                        } catch (e: Exception) {
                            jedis?.disconnect()
                            jedis = null
                            println(e)
                        }
                    }

                    call.respond(mapOf("submission_id" to submissionId))
                }

                authenticate(SUPER_USER_AUTHENTICATION_NAME) {
                    post("/restart") {
                        var unjudgedSubmissionData: List<JudgerSubmissionData>? = null
                        var isOK = true

                        transaction {
                            unjudgedSubmissionData =
                                SubmissionTable.select {
                                    SubmissionTable.result.eq(SUBMISSION_NO_RESULT)
                                }.map {
                                    val testCaseData = TestCaseTable.select {
                                        TestCaseTable.problemId.eq(it[SubmissionTable.problemId])
                                    }.map {
                                        JudgerTestCaseData(
                                            it[TestCaseTable.input],
                                            it[TestCaseTable.expectedOutput],
                                            it[TestCaseTable.score],
                                            it[TestCaseTable.timeOutSeconds]
                                        )
                                    }

                                    JudgerSubmissionData(
                                        it[SubmissionTable.id],
                                        it[SubmissionTable.language],
                                        it[SubmissionTable.code],
                                        testCaseData
                                    )
                                }.toList()
                        }

                        val judgerSubmissionDataList = unjudgedSubmissionData
                        if (judgerSubmissionDataList != null) {
                            for (judgerSubmissionData in judgerSubmissionDataList) {
                                try {
                                    jedis = jedis.getConnection()
                                    val currentJedisConnection: Jedis = jedis!!
                                    currentJedisConnection.rpush(
                                        judgerSubmissionData.language,
                                        jacksonObjectMapper().writeValueAsString(judgerSubmissionData)
                                    )
                                } catch (e: Exception){
                                    jedis?.disconnect()
                                    jedis = null
                                    println(e)
                                    isOK = false
                                }
                            }
                        }

                        call.respond(mapOf("OK" to isOK))
                    }
                }

                route("/{id}") {
                    get {
                        val requestId = call.parameters["id"]?.toInt() ?:
                            throw BadRequestException("The type of Id is wrong.")
                        var responseData: Submission? = null
                        val userIdAuthorityPrincipal = call.sessions.get<UserIdAuthorityPrincipal>()
                        val userId = userIdAuthorityPrincipal?.userId

                        if (userId == null) throw UnauthorizedException()

                        transaction {
                            val requestSubmission = SubmissionTable.select {
                                SubmissionTable.id.eq(requestId)
                            }.first()

                            if (requestSubmission[SubmissionTable.userId] != userId.toInt()) {
                                throw UnauthorizedException()
                            }

                            responseData = Submission(
                                id = requestSubmission[SubmissionTable.id],
                                language = requestSubmission[SubmissionTable.language],
                                code = requestSubmission[SubmissionTable.code],
                                executedTime = requestSubmission[SubmissionTable.executedTime],
                                result = requestSubmission[SubmissionTable.result],
                                problemId = requestSubmission[SubmissionTable.problemId],
                                userId = requestSubmission[SubmissionTable.userId]
                            )
                        }

                        call.respond(mapOf("data" to responseData))
                    }

                    post("/restart") {
                        val requestId = call.parameters["id"]?.toInt() ?:
                            throw BadRequestException("The type of Id is wrong.")
                        var unjudgedSubmissionData: JudgerSubmissionData? = null
                        var isOK = true
                        val userIdAuthorityPrincipal = call.sessions.get<UserIdAuthorityPrincipal>()
                        val userId = userIdAuthorityPrincipal?.userId

                        if (userId == null) throw UnauthorizedException()

                        transaction {
                            val submissionData =
                                SubmissionTable.select {
                                    SubmissionTable.id.eq(requestId)
                                }.first()

                            if (submissionData[SubmissionTable.userId] != userId.toInt()) {
                                throw UnauthorizedException()
                            }

                            val testCaseData =
                                TestCaseTable.select {
                                    TestCaseTable.problemId.eq(submissionData[SubmissionTable.problemId])
                                }.map {
                                    JudgerTestCaseData(
                                        it[TestCaseTable.input],
                                        it[TestCaseTable.expectedOutput],
                                        it[TestCaseTable.score],
                                        it[TestCaseTable.timeOutSeconds]
                                    )
                                }

                            unjudgedSubmissionData = JudgerSubmissionData(
                                submissionData[SubmissionTable.id],
                                submissionData[SubmissionTable.language],
                                submissionData[SubmissionTable.code],
                                testCaseData
                            )
                        }

                        val judgerSubmissionData = unjudgedSubmissionData
                        if (judgerSubmissionData != null) {
                            try {
                                jedis = jedis.getConnection()
                                val currentJedisConnection: Jedis = jedis!!
                                currentJedisConnection.rpush(
                                    judgerSubmissionData.language,
                                    jacksonObjectMapper().writeValueAsString(judgerSubmissionData)
                                )
                            } catch (e: Exception) {
                                println(e)
                                jedis?.disconnect()
                                jedis = null
                                isOK = false
                            }
                        }

                        call.respond(mapOf("OK" to isOK))
                    }
                }
            }
        }
    }
}
