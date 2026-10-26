package com.university.messenger.ui.register

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.university.messenger.data.MessengerGraph
import com.university.messenger.data.MessengerRepository
import com.university.messenger.data.SessionStore
import com.university.messenger.databinding.ActivityRegisterBinding
import com.university.messenger.domain.policy.RegistrationRules
import com.university.messenger.ui.ScreenState
import com.university.messenger.ui.channels.ChannelListActivity
import kotlinx.coroutines.launch

class RegisterViewModel(private val repository: MessengerRepository) : ViewModel() {
    private val stateHolder = MutableLiveData(ScreenState())
    val state: LiveData<ScreenState> = stateHolder

    fun register(email: String, password: String, displayName: String) {
        val errors = RegistrationRules.studentErrors(email, password, displayName)
        if (errors.isNotEmpty()) {
            stateHolder.value = ScreenState(error = errors.joinToString(". "))
            return
        }
        viewModelScope.launch {
            stateHolder.value = ScreenState(loading = true)
            runCatching { repository.register(email.trim(), password, displayName.trim()) }
                .onSuccess { stateHolder.value = ScreenState(done = true) }
                .onFailure { stateHolder.value = ScreenState(error = it.message ?: "Could not register") }
        }
    }
}

class RegisterViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RegisterViewModel(MessengerGraph.repository) as T
    }
}

class RegisterActivity : AppCompatActivity() {
    private val viewModel: RegisterViewModel by viewModels { RegisterViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.serverUrl.setText(SessionStore.baseUrl)
        binding.registerButton.setOnClickListener {
            SessionStore.baseUrl = binding.serverUrl.text.toString()
            viewModel.register(
                binding.email.text.toString(),
                binding.password.text.toString(),
                binding.displayName.text.toString(),
            )
        }
        viewModel.state.observe(this) { state ->
            binding.errorText.visibility = if (state.error == null) View.GONE else View.VISIBLE
            binding.errorText.text = state.error
            binding.registerButton.isEnabled = !state.loading
            if (state.done) {
                startActivity(Intent(this, ChannelListActivity::class.java))
                finish()
            }
        }
    }
}
