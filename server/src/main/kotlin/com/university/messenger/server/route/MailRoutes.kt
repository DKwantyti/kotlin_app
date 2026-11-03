package com.university.messenger.server.route

import com.university.messenger.server.AppContext
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.mailRoutes(context: AppContext) {
    val mail = context.mail ?: return
    route("/mail/{action}") {
        get {
            val action = call.parameters["action"]
            val token = call.request.queryParameters["token"].orEmpty()
            val label = when {
                action == "verify" && verificationToken.matches(token) -> "Potwierdź adres e-mail"
                action == "unsubscribe" && unsubscribeToken.matches(token) -> "Wyłącz powiadomienia e-mail"
                else -> null
            }
            if (label == null) {
                call.mailPage("Nieprawidłowy link.", HttpStatusCode.BadRequest)
            } else {
                call.mailPage(
                    """<form method="post" action="./$action">
                        <input type="hidden" name="token" value="$token">
                        <button type="submit">$label</button>
                       </form>""".trimIndent(),
                )
            }
        }
        post {
            val token = call.receiveParameters()["token"].orEmpty()
            val message = when (call.parameters["action"]) {
                "verify" -> if (verificationToken.matches(token) && mail.verify(token)) {
                    "Adres e-mail potwierdzony. Wróć do UniMessenger."
                } else null
                "unsubscribe" -> if (unsubscribeToken.matches(token) && mail.unsubscribe(token)) {
                    "Powiadomienia e-mail zostały wyłączone."
                } else null
                else -> null
            }
            if (message == null) call.mailPage("Link jest nieprawidłowy lub wygasł.", HttpStatusCode.BadRequest)
            else call.mailPage(message)
        }
    }
}

private val verificationToken = Regex("[A-Za-z0-9_-]{43}")
private val unsubscribeToken = Regex("[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}")

private suspend fun ApplicationCall.mailPage(content: String, status: HttpStatusCode = HttpStatusCode.OK) {
    response.header("Cache-Control", "no-store")
    response.header("Referrer-Policy", "no-referrer")
    response.header("Content-Security-Policy", "default-src 'none'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'")
    respondText(
        """<!doctype html><html lang="pl"><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>UniMessenger</title><main><h1>UniMessenger</h1>$content</main></html>""".trimIndent(),
        ContentType.Text.Html,
        status,
    )
}
