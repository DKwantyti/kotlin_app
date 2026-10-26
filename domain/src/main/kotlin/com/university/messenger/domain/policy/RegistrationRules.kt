package com.university.messenger.domain.policy

object RegistrationRules {
    private val emailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun studentErrors(email: String, password: String, displayName: String): List<String> {
        return buildList {
            if (!emailPattern.matches(email.trim())) add("Invalid email")
            if (password.length < 8 || password.none { it.isLetter() } || password.none { it.isDigit() }) {
                add("Password must be at least 8 characters and include a letter and a digit")
            }
            if (displayName.trim().length !in 2..120) add("Name must be 2 to 120 characters")
        }
    }
}
