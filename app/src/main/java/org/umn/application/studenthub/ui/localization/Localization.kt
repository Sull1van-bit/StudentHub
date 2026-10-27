package org.umn.application.studenthub.ui.localization

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

enum class AppLanguage(val code: String, val displayName: String) {
    INDONESIAN("id", "Bahasa Indonesia"),
    ENGLISH("en", "English")
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.INDONESIAN }

object LanguagePreferences {
    private const val PREFS = "studenthub_prefs"
    private const val KEY = "app_language"

    fun read(context: Context): AppLanguage {
        val code = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "id")
        return if (code == "en") AppLanguage.ENGLISH else AppLanguage.INDONESIAN
    }

    fun write(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.code)
            .apply()
    }
}

@Composable
fun lString(id: String, en: String): String {
    return if (LocalAppLanguage.current == AppLanguage.ENGLISH) en else id
}
