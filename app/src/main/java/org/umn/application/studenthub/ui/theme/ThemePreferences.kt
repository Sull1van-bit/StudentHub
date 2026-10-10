package org.umn.application.studenthub.ui.theme

import android.content.Context

enum class ThemeChoice(val storageValue: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromStorage(value: String?): ThemeChoice = entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

object ThemePreferences {
    private const val PREFS = "studenthub_settings"
    private const val THEME = "theme"

    fun read(context: Context): ThemeChoice = ThemeChoice.fromStorage(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(THEME, null)
    )

    fun write(context: Context, choice: ThemeChoice) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(THEME, choice.storageValue).apply()
    }
}
