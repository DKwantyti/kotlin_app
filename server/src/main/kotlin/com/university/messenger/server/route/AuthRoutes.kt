package com.university.messenger.server.route

import com.university.messenger.domain.api.AuthResponse
import com.university.messenger.domain.api.MailPreferences
import com.university.messenger.domain.api.UpdateMailPreferences
import com.university.messenger.server.AppException
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
            context.sendRegistrationEmail(result.account)
            call.respond(
                HttpStatusCode.Created,
                AuthResponse(result.token, result.account.toResponse()),
            )
        }
        post("/register/teacher") {
            val request = call.receive<RegisterTeacherRequest>()
            val result = context.auth.registerTeacher(request.email, request.password, request.displayName, request.code)
            context.sendRegistrationEmail(result.account)
            call.respond(HttpStatusCode.Created, AuthResponse(result.token, result.account.toResponse()))
        }
        get("/me") { call.respond(call.account(context, requireVerified = false).toResponse()) }
        patch("/me") {
            val actor = call.account(context, requireVerified = false)
            val request = call.receive<UpdateProfileRequest>()
            call.respond(context.auth.updateProfile(actor, request.displayName).toResponse())
        }
        get("/me/mail") {
            val actor = call.account(context, requireVerified = false)
            call.respond(context.mail?.preferences(actor.id) ?: MailPreferences(false, false, false))
        }
        patch("/me/mail") {
            val actor = call.account(context, requireVerified = false)
            val mail = context.mail ?: throw AppException.BadRequest("Email is unavailable")
            call.respond(mail.updatePreferences(actor.id, call.receive<UpdateMailPreferences>().notifications))
        }
        post("/me/mail/verification") {
            val actor = call.account(context, requireVerified = false)
            val mail = context.mail ?: throw AppException.BadRequest("Email is unavailable")
            mail.sendVerification(actor)
            call.respond(HttpStatusCode.NoContent)
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

private suspend fun AppContext.sendRegistrationEmail(account: com.university.messenger.domain.model.Account) {
    try {
        mail?.sendVerification(account)
    } catch (_: AppException.BadRequest) {
        // The account exists and the profile can retry delivery.
    }
}
