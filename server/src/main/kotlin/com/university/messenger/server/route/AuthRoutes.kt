package com.university.messenger.server.route

import com.university.messenger.domain.api.AuthResponse
import com.university.messenger.domain.api.LoginRequest
import com.university.messenger.domain.api.RegisterTeacherRequest
import com.university.messenger.domain.api.RegisterStudentRequest
import com.university.messenger.server.AppContext
import com.university.messenger.server.toResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import com.university.messenger.domain.api.UpdateProfileRequest
import io.ktor.server.routing.route

fun Route.authRoutes(context: AppContext) {
    route("/api/auth") {
        post("/register") {
            val request = call.receive<RegisterStudentRequest>()
            val result = context.auth.registerStudent(request.email, request.password, request.displayName)
            call.respond(
                HttpStatusCode.Created,
                AuthResponse(result.token, result.account.toResponse()),
            )
        }
        post("/register/teacher") {
            val request = call.receive<RegisterTeacherRequest>()
            val result = context.auth.registerTeacher(request.email, request.password, request.displayName, request.code)
            call.respond(HttpStatusCode.Created, AuthResponse(result.token, result.account.toResponse()))
        }
        get("/me") { call.respond(call.account(context).toResponse()) }
        patch("/me") {
            val actor = call.account(context)
            val request = call.receive<UpdateProfileRequest>()
            call.respond(context.auth.updateProfile(actor, request.displayName).toResponse())
        }
        post("/login") {
            val request = call.receive<LoginRequest>()
            val result = context.auth.login(request.email, request.password)
            call.respond(
                AuthResponse(result.token, result.account.toResponse()),
            )
        }
    }
}
