package com.university.messenger.ui.register

import android.content.Intent
import com.university.messenger.ui.fitWindowContent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.university.messenger.R
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

    fun register(email: String, password: String, displayName: String, teacherCode: String?) {
        val errors = RegistrationRules.studentErrors(email, password, displayName)
        if (errors.isNotEmpty()) {
            stateHolder.value = ScreenState(error = errors.joinToString(". "))
            return
        }
        viewModelScope.launch {
            stateHolder.value = ScreenState(loading = true)
            runCatching {
                if (teacherCode == null) repository.register(email.trim(), password, displayName.trim())
                else repository.registerTeacher(email.trim(), password, displayName.trim(), teacherCode)
            }
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
    private lateinit var binding: ActivityRegisterBinding
    private var confirmingTeacher = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitWindowContent(binding.root)
        binding.teacherAccount.isChecked = savedInstanceState?.getBoolean("teacher_account") == true
        binding.teacherAccount.setOnCheckedChangeListener { _, checked ->
            if (!checked) binding.teacherCode.text?.clear()
            showConfirmation(checked && confirmingTeacher)
        }
        showConfirmation(savedInstanceState?.getBoolean("confirming_teacher") == true)
        binding.serverUrl.setText(SessionStore.baseUrl)
        binding.registerButton.setOnClickListener {
            SessionStore.baseUrl = binding.serverUrl.text.toString()
            val teacher = binding.teacherAccount.isChecked
            if (teacher && !confirmingTeacher) {
                showConfirmation(true)
                binding.teacherCode.requestFocus()
                return@setOnClickListener
            }
            val code = if (teacher) binding.teacherCode.text.toString() else null
            if (code != null && !code.matches(Regex("[0-9]{6}"))) {
                binding.teacherCode.error = getString(R.string.teacher_code_error)
                return@setOnClickListener
            }
            viewModel.register(
                binding.email.text.toString(),
                binding.password.text.toString(),
                binding.displayName.text.toString(),
                code,
            )
        }
        viewModel.state.observe(this) { state ->
            binding.errorText.visibility = if (state.error == null) View.GONE else View.VISIBLE
            binding.errorText.text = state.error
            binding.registerButton.isEnabled = !state.loading
            binding.loading.visibility = if (state.loading) View.VISIBLE else View.GONE
            binding.teacherAccount.isEnabled = !state.loading
            if (state.done) {
                startActivity(Intent(this, ChannelListActivity::class.java))
                finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("confirming_teacher", confirmingTeacher)
        outState.putBoolean("teacher_account", binding.teacherAccount.isChecked)
        super.onSaveInstanceState(outState)
    }

    private fun showConfirmation(show: Boolean) {
        confirmingTeacher = show
        binding.teacherConfirmation.isVisible = show
        binding.registerButton.setText(
            when {
                show -> R.string.verify_teacher
                binding.teacherAccount.isChecked -> R.string.teacher_continue
                else -> R.string.register
            },
        )
    }
}
