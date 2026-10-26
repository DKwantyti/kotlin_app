package com.university.messenger.server

import com.university.messenger.domain.api.ErrorResponse
import com.university.messenger.server.config.AppConfig
import com.university.messenger.server.route.authRoutes
import com.university.messenger.server.route.channelRoutes
import com.university.messenger.server.route.messageRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

val AppContextKey = AttributeKey<AppContext>("AppContext")

fun main() {
    val config = AppConfig.fromEnv()
    val context = productionContext(config)
    println("UniMessenger API http://0.0.0.0:${config.port}")
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(context)
    }.start(wait = true)
}

fun Application.module(context: AppContext) {
    attributes.put(AppContextKey, context)
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(CallLogging)
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }
    install(StatusPages) {
        exception<AppException> { call, cause ->
            val status = when (cause) {
                is AppException.BadRequest -> HttpStatusCode.BadRequest
                is AppException.Unauthorized -> HttpStatusCode.Unauthorized
                is AppException.Forbidden -> HttpStatusCode.Forbidden
                is AppException.NotFound -> HttpStatusCode.NotFound
                is AppException.Conflict -> HttpStatusCode.Conflict
            }
            call.respond(status, ErrorResponse(cause.message ?: "Error"))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request"))
        }
        exception<SerializationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request"))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Request failed", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Server error"))
        }
    }
    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }
        authRoutes(context)
        channelRoutes(context)
        messageRoutes(context)
    }
}
