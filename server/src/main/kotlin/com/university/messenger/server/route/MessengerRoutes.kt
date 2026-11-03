package com.university.messenger.server.route

import com.university.messenger.domain.api.CreateGroupRequest
import com.university.messenger.domain.api.GroupMailRequest
import com.university.messenger.domain.api.AddChannelMemberRequest
import com.university.messenger.domain.api.EditMessageRequest
import com.university.messenger.domain.api.PostMessageRequest
import com.university.messenger.domain.api.ReactionRequest
import com.university.messenger.server.AppContext
import com.university.messenger.server.AppException
import com.university.messenger.server.toResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import java.io.InputStream

fun Route.channelRoutes(context: AppContext) {
    get("/api/teachers") {
        val actor = call.account(context)
        call.respond(context.channels.listTeachers(actor).map { it.toResponse() })
    }
    route("/api/channels") {
        get {
            val actor = call.account(context)
            call.respond(context.channels.list(actor).map { it.toResponse(actor.id) })
        }
        post {
            val actor = call.account(context)
            val request = call.receive<CreateGroupRequest>()
            val channel = context.channels.createGroup(actor, request.name, request.description)
            call.respond(HttpStatusCode.Created, channel.toResponse(actor.id))
        }
        post("/{id}/members") {
            val actor = call.account(context)
            val request = call.receive<AddChannelMemberRequest>()
            context.channels.addMember(actor, call.pathId("id"), request.email)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/{id}/mail") {
            val actor = call.account(context)
            val mail = context.mail ?: throw AppException.BadRequest("Email is unavailable")
            val request = call.receive<GroupMailRequest>()
            call.respond(mail.sendGroupMail(actor, call.pathId("id"), request.subject, request.body))
        }
        get("/{id}/messages") {
            val actor = call.account(context)
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val query = call.request.queryParameters["q"].orEmpty()
            val messages = context.messages.history(actor, call.pathId("id"), limit, query)
            call.respond(messages.map { it.toResponse() })
        }
        post("/{id}/messages") {
            val actor = call.account(context)
            val request = call.receive<PostMessageRequest>()
            val message = context.messages.post(actor, call.pathId("id"), request.body, request.replyToId)
            call.respond(HttpStatusCode.Created, message.toResponse())
            context.live.message(message.channelId, message.toResponse())
            context.mail?.notifyDirectMessage(message.channelId, actor.id)
        }
        post("/{id}/files") {
            val actor = call.account(context)
            val channelId = call.pathId("id")
            val upload = readUpload(call.receiveMultipart())
            upload.stream.use { source ->
                val message = context.messages.upload(
                    actor = actor,
                    channelId = channelId,
                    fileName = upload.fileName,
                    sizeHint = upload.sizeHint,
                    body = upload.body,
                    source = source,
                    replyToId = upload.replyToId,
                )
                call.respond(HttpStatusCode.Created, message.toResponse())
                context.live.message(channelId, message.toResponse())
                context.mail?.notifyDirectMessage(channelId, actor.id)
            }
        }
        webSocket("/{id}/live") {
            val actor = try {
                call.account(context)
            } catch (error: AppException) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, error.message ?: "Unauthorized"))
                return@webSocket
            }
            val channelId = try {
                call.pathId("id")
            } catch (error: AppException) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, error.message ?: "Invalid id"))
                return@webSocket
            }
            try {
                context.messages.ensureVisible(actor, channelId)
            } catch (error: AppException) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, error.message ?: "Not allowed"))
                return@webSocket
            }
            context.live.join(channelId, this)
        }
    }
    post("/api/direct/{userId}") {
        val actor = call.account(context)
        val channel = context.channels.openDirect(actor, call.pathId("userId"))
        call.respond(channel.toResponse(actor.id))
    }
}

fun Route.messageRoutes(context: AppContext) {
    patch("/api/messages/{id}/reaction") {
        val actor = call.account(context)
        val request = call.receive<ReactionRequest>()
        val message = context.messages.react(actor, call.pathId("id"), request.emoji)
        context.live.message(message.channelId, message.toResponse())
        call.respond(message.toResponse())
    }
    patch("/api/messages/{id}") {
        val actor = call.account(context)
        val request = call.receive<EditMessageRequest>()
        val message = context.messages.edit(actor, call.pathId("id"), request.body)
        call.respond(message.toResponse())
        context.live.message(message.channelId, message.toResponse())
    }
    delete("/api/messages/{id}") {
        val actor = call.account(context)
        val messageId = call.pathId("id")
        val channelId = context.messages.delete(actor, messageId, call.deletionMode())
        if (channelId != null) context.live.deleted(channelId, messageId)
        call.respond(HttpStatusCode.NoContent)
    }
    get("/api/attachments/{id}") {
        val actor = call.account(context)
        val opened = context.messages.open(actor, call.pathId("id"))
        call.response.header(
            HttpHeaders.ContentDisposition,
            "attachment; filename=\"${opened.fileName.replace("\"", "")}\"",
        )
        opened.stream.use { input ->
            call.respondOutputStream(ContentType.parse(opened.contentType)) {
                input.copyTo(this)
            }
        }
    }
}

private data class IncomingUpload(
    val fileName: String,
    val sizeHint: Long?,
    val body: String,
    val stream: InputStream,
    val replyToId: String?,
)

private suspend fun readUpload(multipart: io.ktor.http.content.MultiPartData): IncomingUpload {
    var body = ""
    var replyToId: String? = null
    var fileName: String? = null
    var sizeHint: Long? = null
    var stream: InputStream? = null
    multipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> when (part.name) {
                "body" -> body = part.value
                "replyToId" -> replyToId = part.value
                else -> Unit
            }
            is PartData.FileItem -> {
                fileName = part.originalFileName ?: "file"
                sizeHint = part.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                stream = part.provider().toInputStream()
            }
            else -> Unit
        }
        if (part !is PartData.FileItem) part.release()
    }
    return IncomingUpload(
        fileName = fileName ?: throw AppException.BadRequest("Missing file"),
        sizeHint = sizeHint,
        body = body,
        replyToId = replyToId,
        stream = stream ?: throw AppException.BadRequest("Missing file"),
    )
}
