package com.university.messenger.server.realtime

import com.university.messenger.domain.api.LiveEvent
import com.university.messenger.domain.api.MessageResponse
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Open chat sockets for one process. A new, edited, or deleted message is sent
 * to everyone who currently has that channel open.
 */
class ChannelLive(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val sessions = ConcurrentHashMap<UUID, CopyOnWriteArrayList<WebSocketSession>>()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun join(channelId: UUID, session: WebSocketSession) {
        val room = sessions.computeIfAbsent(channelId) { CopyOnWriteArrayList() }
        room.add(session)
        try {
            session.send(Frame.Text(json.encodeToString(LiveEvent(type = "ready"))))
            for (frame in session.incoming) {
                if (frame is Frame.Close) break
            }
        } finally {
            room.remove(session)
            if (room.isEmpty()) sessions.remove(channelId, room)
        }
    }

    fun message(channelId: UUID, message: MessageResponse) {
        scope.launch { broadcast(channelId, LiveEvent(type = "message", message = message)) }
    }

    fun deleted(channelId: UUID, messageId: UUID) {
        scope.launch { broadcast(channelId, LiveEvent(type = "deleted", messageId = messageId.toString())) }
    }

    private suspend fun broadcast(channelId: UUID, event: LiveEvent) {
        val room = sessions[channelId] ?: return
        val text = json.encodeToString(event)
        val gone = mutableListOf<WebSocketSession>()
        for (session in room) {
            try {
                session.send(Frame.Text(text))
            } catch (_: Exception) {
                gone += session
            }
        }
        if (gone.isNotEmpty()) room.removeAll(gone)
    }
}
