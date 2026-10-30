package com.university.messenger.ui.chat

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.university.messenger.R
import com.university.messenger.data.MessengerGraph
import com.university.messenger.data.MessengerRepository
import com.university.messenger.databinding.ActivityChatBinding
import com.university.messenger.databinding.ItemMessageBinding
import com.university.messenger.domain.api.MessageResponse
import com.university.messenger.domain.api.LiveEvent
import com.university.messenger.domain.policy.FileUploadPolicy
import com.university.messenger.domain.policy.UploadDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

data class ChatState(
    val loading: Boolean = false,
    val messages: List<MessageResponse> = emptyList(),
    val error: String? = null,
)

class ChatViewModel(
    private val repository: MessengerRepository,
    private val channelId: String,
) : ViewModel() {
    private val stateHolder = MutableLiveData(ChatState())
    val state: LiveData<ChatState> = stateHolder
    private val policy = FileUploadPolicy()

    init {
        viewModelScope.launch {
            while (isActive) {
                try {
                    repository.listen(channelId, ::applyLive)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // The socket drops when the server is restarted. Try again.
                }
                delay(2_000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.messages(channelId) }
                .onSuccess { stateHolder.value = ChatState(messages = it) }
                .onFailure { stateHolder.value = ChatState(error = it.message) }
        }
    }

    fun send(body: String) {
        val text = body.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.sendMessage(channelId, text) }
                .onSuccess { refresh() }
                .onFailure { stateHolder.value = stateHolder.value?.copy(error = it.message) }
        }
    }

    fun sendFile(name: String, size: Long, caption: String, open: () -> InputStream) {
        when (val decision = policy.evaluate(name, size.takeIf { it > 0L })) {
            is UploadDecision.Rejected -> stateHolder.value = stateHolder.value?.copy(error = decision.reason)
            UploadDecision.Accepted -> viewModelScope.launch {
                runCatching {
                    open().use { source ->
                        repository.upload(channelId, name, size, caption, source)
                    }
                }.onSuccess { refresh() }
                    .onFailure { stateHolder.value = stateHolder.value?.copy(error = it.message) }
            }
        }
    }

    fun download(attachmentId: String, fileName: String, cacheDir: File, onSaved: (File) -> Unit) {
        viewModelScope.launch {
            val target = File(cacheDir, fileName.substringAfterLast('/'))
            runCatching { repository.download(attachmentId, target) }
                .onSuccess { onSaved(target) }
                .onFailure { stateHolder.value = stateHolder.value?.copy(error = it.message) }
        }
    }

    private fun applyLive(event: LiveEvent) {
        val current = stateHolder.value ?: ChatState()
        val next = when (event.type) {
            "deleted" -> current.messages.filterNot { it.id == event.messageId }
            "message" -> {
                val message = event.message ?: return
                (current.messages.filterNot { it.id == message.id } + message).sortedBy { it.createdAt }
            }
            else -> return
        }
        stateHolder.postValue(current.copy(messages = next, error = null))
    }
}

class ChatViewModelFactory(private val channelId: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(MessengerGraph.repository, channelId) as T
    }
}

class ChatActivity : AppCompatActivity() {
    private val channelId: String by lazy { intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty() }
    private val viewModel: ChatViewModel by viewModels { ChatViewModelFactory(channelId) }
    private lateinit var binding: ActivityChatBinding
    private val adapter = MessageAdapter { message ->
        val file = message.attachments.firstOrNull() ?: return@MessageAdapter
        viewModel.download(file.id, file.fileName, cacheDir) { saved ->
            Toast.makeText(this, getString(R.string.saved_file) + ": " + saved.name, Toast.LENGTH_SHORT).show()
        }
    }
    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) sendPickedFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.title.text = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        binding.messageList.layoutManager = LinearLayoutManager(this)
        binding.messageList.adapter = adapter
        binding.sendButton.setOnClickListener {
            viewModel.send(binding.messageInput.text.toString())
            binding.messageInput.setText("")
        }
        binding.attachButton.setOnClickListener { pickFile.launch("*/*") }
        viewModel.state.observe(this) { state ->
            adapter.submit(state.messages)
            binding.emptyText.visibility = if (state.messages.isEmpty()) View.VISIBLE else View.GONE
            binding.errorText.visibility = if (state.error == null) View.GONE else View.VISIBLE
            binding.errorText.text = state.error
            if (state.messages.isNotEmpty()) binding.messageList.scrollToPosition(state.messages.lastIndex)
        }
        viewModel.refresh()
    }

    private fun sendPickedFile(uri: Uri) {
        val caption = binding.messageInput.text?.toString().orEmpty()
        var name = "plik"
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        viewModel.sendFile(name, size, caption) {
            contentResolver.openInputStream(uri) ?: error("Cannot open file")
        }
        binding.messageInput.setText("")
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "channel_id"
        const val EXTRA_TITLE = "title"
    }
}

private class MessageAdapter(
    private val onClick: (MessageResponse) -> Unit,
) : RecyclerView.Adapter<MessageAdapter.Holder>() {
    private val items = mutableListOf<MessageResponse>()

    fun submit(data: List<MessageResponse>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.binding.author.text = item.authorName
        holder.binding.body.text = item.body
        val files = item.attachments.joinToString { it.fileName }
        holder.binding.meta.text = listOf(item.createdAt.take(16).replace('T', ' '), files)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class Holder(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)
}
