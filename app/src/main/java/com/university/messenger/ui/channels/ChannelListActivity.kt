package com.university.messenger.ui.channels

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
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
import com.university.messenger.databinding.ActivityChannelsBinding
import com.university.messenger.databinding.ItemChannelBinding
import com.university.messenger.domain.api.ChannelResponse
import com.university.messenger.domain.api.UserResponse
import com.university.messenger.ui.chat.ChatActivity
import com.university.messenger.ui.logout
import kotlinx.coroutines.launch

data class ChannelScreenState(
    val loading: Boolean = false,
    val channels: List<ChannelResponse> = emptyList(),
    val error: String? = null,
    val opened: ChannelResponse? = null,
)

class ChannelListViewModel(private val repository: MessengerRepository) : ViewModel() {
    private val stateHolder = MutableLiveData(ChannelScreenState())
    val state: LiveData<ChannelScreenState> = stateHolder

    fun refresh() {
        viewModelScope.launch {
            stateHolder.value = ChannelScreenState(loading = true)
            runCatching { repository.channels() }
                .onSuccess { stateHolder.value = ChannelScreenState(channels = it) }
                .onFailure { stateHolder.value = ChannelScreenState(error = it.message) }
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch {
            runCatching { repository.createGroup(name) }
                .onSuccess { refresh() }
                .onFailure { stateHolder.value = stateHolder.value?.copy(error = it.message) }
        }
    }

    fun loadTeachers(onReady: (List<UserResponse>) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.teachers() }
                .onSuccess { onReady(it) }
                .onFailure { stateHolder.value = stateHolder.value?.copy(error = it.message) }
        }
    }

    fun openDirect(userId: String) {
        viewModelScope.launch {
            runCatching { repository.openDirect(userId) }
                .onSuccess { stateHolder.value = stateHolder.value?.copy(opened = it, error = null) }
                .onFailure { stateHolder.value = stateHolder.value?.copy(error = it.message) }
        }
    }

    fun consumeOpened() {
        stateHolder.value = stateHolder.value?.copy(opened = null)
    }
}

class ChannelListViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChannelListViewModel(MessengerGraph.repository) as T
    }
}

class ChannelListActivity : AppCompatActivity() {
    private val viewModel: ChannelListViewModel by viewModels { ChannelListViewModelFactory() }
    private val adapter = ChannelAdapter { channel -> openChat(channel) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityChannelsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.channelList.layoutManager = LinearLayoutManager(this)
        binding.channelList.adapter = adapter
        binding.newGroupButton.setOnClickListener { askForGroup() }
        binding.teachersButton.setOnClickListener { pickTeacher() }
        binding.logoutButton.setOnClickListener { logout() }
        viewModel.state.observe(this) { state ->
            adapter.submit(state.channels)
            binding.emptyText.visibility = if (state.channels.isEmpty() && !state.loading) View.VISIBLE else View.GONE
            binding.errorText.visibility = if (state.error == null) View.GONE else View.VISIBLE
            binding.errorText.text = state.error
            state.opened?.let { channel ->
                viewModel.consumeOpened()
                openChat(channel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun openChat(channel: ChannelResponse) {
        startActivity(
            Intent(this, ChatActivity::class.java)
                .putExtra(ChatActivity.EXTRA_CHANNEL_ID, channel.id)
                .putExtra(ChatActivity.EXTRA_TITLE, channel.title),
        )
    }

    private fun askForGroup() {
        val input = EditText(this)
        input.hint = getString(R.string.group_name)
        AlertDialog.Builder(this)
            .setTitle(R.string.new_group)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.length >= 2) viewModel.createGroup(name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pickTeacher() {
        viewModel.loadTeachers { teachers ->
            if (teachers.isEmpty()) return@loadTeachers
            val names = teachers.map { it.displayName }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(R.string.teachers)
                .setItems(names) { _, index -> viewModel.openDirect(teachers[index].id) }
                .show()
        }
    }
}

private class ChannelAdapter(
    private val onClick: (ChannelResponse) -> Unit,
) : RecyclerView.Adapter<ChannelAdapter.Holder>() {
    private val items = mutableListOf<ChannelResponse>()

    fun submit(data: List<ChannelResponse>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.binding.channelTitle.text = item.title
        holder.binding.channelKind.text = if (item.kind == "DIRECT") "Chat" else "Group"
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class Holder(val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root)
}
