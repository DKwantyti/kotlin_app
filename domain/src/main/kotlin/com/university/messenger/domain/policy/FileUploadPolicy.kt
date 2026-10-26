package com.university.messenger.domain.policy

class FileUploadPolicy(
    private val maxBytes: Long = MAX_BYTES,
    blockedExtensions: Set<String> = BLOCKED_EXTENSIONS,
) {
    private val blocked = blockedExtensions.map { it.lowercase() }.toSet()

    fun evaluate(originalName: String, sizeBytes: Long?): UploadDecision {
        val name = originalName.trim()
        if (name.isEmpty() || name.length > 255 || name.contains('/') || name.contains('\\') || name.contains('\u0000')) {
            return UploadDecision.Rejected("Invalid file name")
        }
        val parts = name.lowercase().split('.').filter { it.isNotBlank() }
        if (parts.size < 2) {
            return UploadDecision.Rejected("File must have an extension")
        }
        val blockedHit = parts.drop(1).firstOrNull { it in blocked }
        if (blockedHit != null) {
            return UploadDecision.Rejected("Extension .$blockedHit is blocked")
        }
        if (sizeBytes != null && sizeBytes <= 0L) {
            return UploadDecision.Rejected("File is empty")
        }
        if (sizeBytes != null && sizeBytes > maxBytes) {
            return UploadDecision.Rejected("File exceeds 1 GB")
        }
        return UploadDecision.Accepted
    }

    companion object {
        const val MAX_BYTES: Long = 1024L * 1024L * 1024L

        val BLOCKED_EXTENSIONS: Set<String> = setOf(
            "exe", "dmg", "bat", "cmd", "com", "msi", "scr", "dll", "apk",
            "jar", "sh", "ps1", "vbs", "app", "pkg", "deb", "rpm", "bin",
            "iso", "gadget", "hta", "js", "jse", "vbe", "wsf", "cpl", "sys",
            "drv", "msc",
        )
    }
}

sealed interface UploadDecision {
    data object Accepted : UploadDecision
    data class Rejected(val reason: String) : UploadDecision
}
