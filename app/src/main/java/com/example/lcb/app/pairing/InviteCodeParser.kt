package com.example.lcb.app.pairing

object InviteCodeParser {
    private val codeRegex = Regex("[A-Z0-9]{4,12}")

    fun parse(rawValue: String): String? {
        val trimmed = rawValue.trim().uppercase()
        val codeFromQuery = runCatching {
            android.net.Uri.parse(trimmed).getQueryParameter("CODE")?.uppercase()
        }.getOrNull()
        if (!codeFromQuery.isNullOrBlank() && codeRegex.matches(codeFromQuery)) {
            return codeFromQuery
        }
        return codeRegex.find(trimmed)?.value
    }
}
