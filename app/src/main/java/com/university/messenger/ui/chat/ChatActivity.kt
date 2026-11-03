package com.university.messenger.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import com.university.messenger.domain.api.GroupMailResult
import android.text.InputFilter
import com.university.messenger.domain.model.DeletionMode
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.google.android.material.chip.Chip
import com.university.messenger.data.SessionStore
import com.university.messenger.domain.api.messageReactionEmoji
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
import kotlinx.coroutines.Job
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
    var replyTarget: MessageResponse? = null
    private var refreshJob: Job? = null
    private var refreshPending = false

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
        refreshPending = true
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            while (refreshPending) {
                refreshPending = false
                runCatching { repository.messages(channelId) }
                    .onSuccess { history ->
                        if (!refreshPending) stateHolder.value = ChatState(messages = history)
                    }
                    .onFailure { showError(it) }
            }
        }
    }

    fun send(body: String, replyToId: String?, onSent: () -> Unit) {
        val text = body.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.sendMessage(channelId, text, replyToId) }
                .onSuccess {
                    onSent()
                    refresh()
                }
                .onFailure { showError(it) }
        }
    }

    fun search(query: String, onResults: (List<MessageResponse>) -> Unit, onError: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.messages(channelId, query) }
                .onSuccess(onResults)
                .onFailure {
                    if (it is CancellationException) throw it
                    onError(it.message)
                }
        }
    }

    fun sendGroupMail(subject: String, body: String, onResult: (GroupMailResult) -> Unit, onError: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.sendGroupMail(channelId, subject, body) }
                .onSuccess(onResult)
                .onFailure {
                    if (it is CancellationException) throw it
                    onError(it.message)
                }
        }
    }

    fun addMember(email: String, onAdded: () -> Unit, onError: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.addChannelMember(channelId, email) }
                .onSuccess { onAdded() }
                .onFailure {
                    if (it is CancellationException) throw it
                    onError(it.message)
                }
        }
    }

    fun edit(message: MessageResponse, body: String) {
        viewModelScope.launch {
            runCatching { repository.editMessage(message.id, body) }
                .onSuccess { refresh() }
                .onFailure { showError(it) }
        }
    }

    fun delete(message: MessageResponse, mode: DeletionMode) {
        viewModelScope.launch {
            runCatching { repository.deleteMessage(message.id, mode) }
                .onSuccess {
                    if (replyTarget?.id == message.id) replyTarget = null
                    refresh()
                }
                .onFailure { showError(it) }
        }
    }

    fun react(message: MessageResponse, emoji: String) {
        val selected = message.reactions[SessionStore.userId]
        viewModelScope.launch {
            runCatching { repository.react(message.id, emoji.takeUnless { it == selected }) }
                .onSuccess { refresh() }
                .onFailure { showError(it) }
        }
    }

    private fun showError(error: Throwable) {
        if (error is CancellationException) throw error
        stateHolder.value = stateHolder.value?.copy(error = error.message)
    }

    fun sendFile(
        name: String,
        size: Long,
        caption: String,
        replyToId: String?,
        onSent: () -> Unit,
        open: () -> InputStream,
    ) {
        when (val decision = policy.evaluate(name, size.takeIf { it > 0L })) {
            is UploadDecision.Rejected -> stateHolder.value = stateHolder.value?.copy(error = decision.reason)
            UploadDecision.Accepted -> viewModelScope.launch {
                runCatching {
                    open().use { source ->
                        repository.upload(channelId, name, size, caption, source, replyToId)
                    }
                }.onSuccess {
                    onSent()
                    refresh()
                }.onFailure { showError(it) }
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
        if (event.type in setOf("ready", "message", "deleted")) refresh()
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
    private val adapter by lazy { MessageAdapter(::download, ::showActions, ::jumpTo, viewModel::react) }

    private fun download(message: MessageResponse) {
        val file = message.attachments.firstOrNull() ?: return
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val padding = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.updatePadding(padding.left, padding.top, padding.right, padding.bottom)
            insets
        }
        binding.toolbar.title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        binding.toolbar.setNavigationIcon(R.drawable.ic_chat_back)
        binding.toolbar.setNavigationContentDescription(R.string.chat_back)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.menu.add(R.string.chat_search).setOnMenuItemClickListener {
            showSearch()
            true
        }
        if (intent.getBooleanExtra(EXTRA_MANAGE_MEMBERS, false)) {
            if (SessionStore.role == "TEACHER") {
                binding.toolbar.menu.add(R.string.group_mail).setOnMenuItemClickListener {
                    showGroupMail()
                    true
                }
            }
            binding.toolbar.menu.add(R.string.chat_add_member).setOnMenuItemClickListener {
                showAddMember()
                true
            }
        }
        binding.messageList.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.cancelReply.setOnClickListener { selectReply(null) }
        selectReply(viewModel.replyTarget)
        binding.messageList.adapter = adapter
        binding.sendButton.setOnClickListener {
            val draft = binding.messageInput.text.toString()
            val target = viewModel.replyTarget
            viewModel.send(draft, target?.id) { clearDraft(draft, target) }
        }
        binding.attachButton.setOnClickListener { pickFile.launch("*/*") }
        viewModel.state.observe(this) { state ->
            val atBottom = !binding.messageList.canScrollVertically(1)
            adapter.submit(state.messages) {
                if (atBottom && adapter.itemCount > 0) binding.messageList.scrollToPosition(adapter.itemCount - 1)
            }
            binding.emptyText.visibility = if (state.messages.isEmpty()) View.VISIBLE else View.GONE
            binding.errorText.visibility = if (state.error == null) View.GONE else View.VISIBLE
            binding.errorText.text = state.error
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
        val target = viewModel.replyTarget
        viewModel.sendFile(name, size, caption, target?.id, { clearDraft(caption, target) }) {
            contentResolver.openInputStream(uri) ?: error("Cannot open file")
        }
    }

    private fun clearDraft(text: String, target: MessageResponse?) {
        if (binding.messageInput.text.toString() != text || viewModel.replyTarget != target) return
        binding.messageInput.setText("")
        selectReply(null)
    }

    private fun selectReply(message: MessageResponse?) {
        viewModel.replyTarget = message
        binding.replyBar.isVisible = message != null
        binding.replyDraft.text = message?.let { replyPreview(it) }
    }

    private fun jumpTo(id: String) {
        val position = adapter.currentList.indexOfFirst { it.message.id == id }
        if (position >= 0) binding.messageList.smoothScrollToPosition(position)
        else Toast.makeText(this, R.string.chat_reply_unavailable, Toast.LENGTH_SHORT).show()
    }

    private fun showActions(message: MessageResponse) {
        val actions = mutableListOf(
            getString(R.string.chat_reply) to {
                selectReply(message)
                binding.messageInput.requestFocus()
                getSystemService(InputMethodManager::class.java)
                    .showSoftInput(binding.messageInput, InputMethodManager.SHOW_IMPLICIT)
                Unit
            },
            getString(R.string.chat_react) to {
                val selected = message.reactions[SessionStore.userId]
                val options = messageReactionEmoji.map { if (it == selected) "$it ✓" else it }
                AlertDialog.Builder(this)
                    .setTitle(R.string.chat_react)
                    .setItems(options.toTypedArray()) { _, index -> viewModel.react(message, messageReactionEmoji[index]) }
                    .show()
                Unit
            },
        )
        if (message.authorId == SessionStore.userId) {
            if (message.body.isNotBlank() && SessionStore.role == "TEACHER" &&
                intent.getBooleanExtra(EXTRA_MANAGE_MEMBERS, false)) {
                actions += getString(R.string.group_mail_from_message) to { showGroupMail(message.body) }
            }
            actions += getString(R.string.chat_edit) to { showEdit(message) }
            actions += getString(R.string.chat_delete) to { showDelete(message) }
        }
        if (message.body.isNotBlank()) {
            actions += getString(R.string.chat_copy) to {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText(getString(R.string.message_hint), message.body))
            }
        }
        AlertDialog.Builder(this)
            .setItems(actions.map { it.first }.toTypedArray()) { _, index -> actions[index].second() }
            .show()
    }

    private fun showSearch() {
        val input = EditText(this).apply {
            hint = getString(R.string.chat_search_hint)
            filters = arrayOf(InputFilter.LengthFilter(120))
            setSingleLine()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.chat_search)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.chat_search, null)
            .show()
        val search = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        search.setOnClickListener {
            val query = input.text.toString().trim()
            if (query.isBlank()) {
                input.error = getString(R.string.chat_search_hint)
                return@setOnClickListener
            }
            search.isEnabled = false
            viewModel.search(query, { results ->
                if (dialog.isShowing) {
                    dialog.dismiss()
                    showSearchResults(results.asReversed())
                }
            }, { error ->
                input.error = error ?: getString(R.string.chat_search_error)
                search.isEnabled = true
            })
        }
    }

    private fun showSearchResults(results: List<MessageResponse>) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.chat_search_results)
            .setNegativeButton(R.string.cancel, null)
        if (results.isEmpty()) {
            dialog.setMessage(R.string.chat_search_empty)
        } else {
            dialog.setItems(results.map { replyPreview(it) }.toTypedArray()) { _, index ->
                val message = results[index]
                if (adapter.currentList.any { it.message.id == message.id }) {
                    jumpTo(message.id)
                } else {
                    val details = AlertDialog.Builder(this)
                        .setTitle(message.authorName)
                        .setMessage(listOf(message.body, message.attachments.joinToString("\n") { it.fileName })
                            .filter { it.isNotBlank() }.joinToString("\n\n"))
                        .setNegativeButton(R.string.cancel, null)
                    if (message.attachments.isNotEmpty()) {
                        details.setPositiveButton(R.string.chat_download) { _, _ -> download(message) }
                    }
                    details.show()
                }
            }
        }
        dialog.show()
    }

    private fun showGroupMail(draft: String = "") {
        val subject = EditText(this).apply {
            hint = getString(R.string.group_mail_subject)
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(120))
            setText(getString(R.string.group_mail_template, binding.toolbar.title?.toString().orEmpty().take(90)))
        }
        val body = EditText(this).apply {
            hint = getString(R.string.group_mail_body)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(InputFilter.LengthFilter(4000))
            minLines = 3
            maxLines = 8
            setText(draft)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val inset = (20 * resources.displayMetrics.density).toInt()
            setPadding(inset, 0, inset, 0)
            addView(subject)
            addView(body)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.group_mail)
            .setMessage(R.string.group_mail_help)
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.send, null)
            .show()
        val send = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        send.setOnClickListener {
            val title = subject.text.toString().trim()
            val text = body.text.toString().trim()
            if (title.isBlank() || text.isBlank()) {
                body.error = getString(R.string.group_mail_required)
                return@setOnClickListener
            }
            send.isEnabled = false
            dialog.setCancelable(false)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
            viewModel.sendGroupMail(title, text, { result ->
                dialog.dismiss()
                Toast.makeText(this, getString(R.string.group_mail_result, result.accepted, result.failed), Toast.LENGTH_LONG).show()
            }, { error ->
                body.error = error ?: getString(R.string.group_mail_error)
                send.isEnabled = true
                dialog.setCancelable(true)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
            })
        }
    }

    private fun showAddMember() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            hint = getString(R.string.email)
            setSingleLine()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.chat_add_member)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.chat_add_member, null)
            .show()
        val add = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        add.setOnClickListener {
            add.isEnabled = false
            viewModel.addMember(input.text.toString(), {
                dialog.dismiss()
                Toast.makeText(this, R.string.chat_member_added, Toast.LENGTH_SHORT).show()
            }, { error ->
                input.error = error ?: getString(R.string.chat_member_error)
                add.isEnabled = true
            })
        }
    }

    private fun showEdit(message: MessageResponse) {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(InputFilter.LengthFilter(4000))
            setText(message.body)
            setSelection(length())
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.chat_edit)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val body = input.text.toString().trim()
            if (body.isBlank() && message.attachments.isEmpty()) {
                input.error = getString(R.string.chat_body_required)
            } else {
                viewModel.edit(message, body)
                dialog.dismiss()
            }
        }
    }

    private fun showDelete(message: MessageResponse) {
        AlertDialog.Builder(this)
            .setTitle(R.string.chat_delete)
            .setMessage(R.string.chat_delete_help)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.chat_hide) { _, _ ->
                if (viewModel.replyTarget?.id == message.id) selectReply(null)
                viewModel.delete(message, DeletionMode.SOFT)
            }
            .setPositiveButton(R.string.chat_delete_forever) { _, _ ->
                if (viewModel.replyTarget?.id == message.id) selectReply(null)
                viewModel.delete(message, DeletionMode.HARD)
            }
            .show()
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "channel_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MANAGE_MEMBERS = "manage_members"
    }
}

private fun android.content.Context.replyPreview(message: MessageResponse): String {
    val excerpt = message.body.ifBlank { message.attachments.firstOrNull()?.fileName.orEmpty() }
    return getString(R.string.chat_reply_preview, message.authorName, excerpt.take(160))
}

private data class MessageRow(val message: MessageResponse, val reply: MessageResponse?)

private class MessageAdapter(
    private val onDownload: (MessageResponse) -> Unit,
    private val onActions: (MessageResponse) -> Unit,
    private val onQuote: (String) -> Unit,
    private val onReact: (MessageResponse, String) -> Unit,
) : ListAdapter<MessageRow, MessageAdapter.Holder>(object : DiffUtil.ItemCallback<MessageRow>() {
    override fun areItemsTheSame(old: MessageRow, new: MessageRow) = old.message.id == new.message.id
    override fun areContentsTheSame(old: MessageRow, new: MessageRow) = old == new
}) {
    private val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    fun submit(data: List<MessageResponse>, onCommitted: () -> Unit) {
        val byId = data.associateBy { it.id }
        submitList(data.map { MessageRow(it, byId[it.replyToId]) }) { onCommitted() }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val (message, reply) = getItem(position)
        val binding = holder.binding
        val context = binding.root.context
        val own = message.authorId == SessionStore.userId
        val bubble = binding.bubble
        val gutter = (48 * context.resources.displayMetrics.density).toInt()
        bubble.layoutParams = (bubble.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = if (own) Gravity.END else Gravity.START
            marginStart = if (own) gutter else 0
            marginEnd = if (own) 0 else gutter
        }
        bubble.setBackgroundResource(if (own) R.drawable.chat_outgoing else R.drawable.chat_incoming)
        val actions = View.OnLongClickListener {
            onActions(message)
            true
        }
        bubble.setOnClickListener { onActions(message) }
        bubble.setOnLongClickListener(actions)
        binding.body.setOnLongClickListener(actions)
        binding.files.setOnLongClickListener(actions)
        binding.quote.setOnLongClickListener(actions)
        binding.author.isVisible = !own
        binding.author.text = message.authorName
        binding.body.isVisible = message.body.isNotBlank()
        binding.body.text = message.body
        binding.quote.isVisible = message.replyToId != null
        binding.quote.text = reply?.let { context.replyPreview(it) } ?: context.getString(R.string.chat_reply_unavailable)
        binding.quote.setOnClickListener { message.replyToId?.let(onQuote) }
        binding.files.isVisible = message.attachments.isNotEmpty()
        binding.files.text = message.attachments.joinToString("\n") { "📎 ${it.fileName}" }
        binding.files.setOnClickListener { onDownload(message) }
        val time = runCatching { clock.format(Instant.parse(message.createdAt)) }
            .getOrDefault(message.createdAt.take(16).replace('T', ' '))
        binding.meta.text = if (message.createdAt == message.updatedAt) time else context.getString(R.string.chat_edited, time)

        val counts = message.reactions.values.groupingBy { it }.eachCount()
        binding.reactions.removeAllViews()
        binding.reactions.isVisible = counts.isNotEmpty()
        for ((emoji, count) in counts.entries.sortedBy { messageReactionEmoji.indexOf(it.key) }) {
            val selected = message.reactions[SessionStore.userId] == emoji
            binding.reactions.addView(Chip(context).apply {
                text = "$emoji $count"
                isSelected = selected
                chipBackgroundColor = ColorStateList.valueOf(Color.parseColor(if (selected) "#A8D8F3" else "#EDF3F7"))
                setOnClickListener { onReact(message, emoji) }
            })
        }
    }

    class Holder(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)
}
