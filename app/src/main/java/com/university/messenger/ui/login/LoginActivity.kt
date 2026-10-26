package com.university.messenger.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.university.messenger.data.MessengerGraph
import com.university.messenger.data.MessengerRepository
import com.university.messenger.data.SessionStore
import com.university.messenger.databinding.ActivityLoginBinding
import com.university.messenger.ui.ScreenState
import com.university.messenger.ui.channels.ChannelListActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: MessengerRepository) : ViewModel() {
    private val stateHolder = MutableLiveData(ScreenState())
    val state: LiveData<ScreenState> = stateHolder

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            stateHolder.value = ScreenState(error = "Enter email and password")
            return
        }
        viewModelScope.launch {
            stateHolder.value = ScreenState(loading = true)
            runCatching { repository.login(email.trim(), password) }
                .onSuccess { stateHolder.value = ScreenState(done = true) }
                .onFailure { stateHolder.value = ScreenState(error = it.message ?: "Could not sign in") }
        }
    }
}

class LoginViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LoginViewModel(MessengerGraph.repository) as T
    }
}

class LoginActivity : AppCompatActivity() {
    private val viewModel: LoginViewModel by viewModels { LoginViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SessionStore.token != null) {
            openChannels()
            return
        }
        val binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.serverUrl.setText(SessionStore.baseUrl)
        binding.loginButton.setOnClickListener {
            SessionStore.baseUrl = binding.serverUrl.text.toString()
            viewModel.login(binding.email.text.toString(), binding.password.text.toString())
        }
        binding.registerButton.setOnClickListener {
            SessionStore.baseUrl = binding.serverUrl.text.toString()
            startActivity(Intent(this, com.university.messenger.ui.register.RegisterActivity::class.java))
        }
        viewModel.state.observe(this) { state ->
            binding.errorText.visibility = if (state.error == null) View.GONE else View.VISIBLE
            binding.errorText.text = state.error
            binding.loginButton.isEnabled = !state.loading
            if (state.done) openChannels()
        }
    }

    private fun openChannels() {
        startActivity(Intent(this, ChannelListActivity::class.java))
        finish()
    }
}
