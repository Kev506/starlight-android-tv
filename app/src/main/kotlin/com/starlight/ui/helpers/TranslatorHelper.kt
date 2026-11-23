package com.starlight.ui.helpers

import android.content.Context
import android.content.res.Resources
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.starlight.R
import com.starlight.games.GameDataBaseManager
import com.starlight.games.GameDetailsState
import com.starlight.utils.AppComputerObject
import com.starlight.utils.LimeLog
import com.starlight.utils.normalizeLanguageCode
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Created by Kevin Andrews on 04/06/2026.
 */

class TranslatorHelper(
    private val resources: Resources,
    var uiContext: AppCompatActivity
) {

    var translator: Translator? = null

    // Check if database exists
    fun doesDatabaseExist(context: Context): Boolean {
        val dbFile: File = context.getDatabasePath(GameDataBaseManager.DB_NAME)
        return dbFile.exists()
    }

    // Display Translated Description
    suspend fun displayTranslatedDescription(
        state: GameDetailsState,
        app: AppComputerObject?,
        description: String?, language: String?, isModelDownloaded: Boolean
    ): String? {

        val noDetailsPlaceholder = "No Details"
        if (description == noDetailsPlaceholder || description.isNullOrEmpty()) {
            state.description = resources.getString(R.string.No_Details)
            return state.description
        }

        if (language != "default" && language != "en") {
            val dbHandler =
                GameDataBaseManager.getInstance(uiContext) ?: return description
            if (!dbHandler.isDatabaseEmpty()) {
                val appData = dbHandler.getAppData(app?.app?.id.toString())
                if (!appData.translatedText.isNullOrEmpty() && appData.translatedText != noDetailsPlaceholder) {
                    state.description = appData.translatedText
                    return state.description
                }
            }
        }

        if (isModelDownloaded) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(normalizeLanguageCode(language!!))
                .build()
            translator = Translation.getClient(options)
        }

        if (language != "default" && language != "en" && isModelDownloaded) {
            translator?.translate(description)
                ?.addOnSuccessListener { translatedText ->
                    state.description = translatedText
                }
                ?.addOnFailureListener { exception ->
                    LimeLog.warning("Translation failed: ${exception.message}")
                    state.description = description
                }?.await()
        } else {
            state.description =
                description
        }
        return state.description
    }

    // Translate app description and save it to the database
    suspend fun translateAndSaveAppDescription(
        appId: String,
        description: String?,
        language: String?,
        isModelDownloaded: Boolean
    ): String? {
        if (description.isNullOrEmpty() || language.isNullOrEmpty() || !isModelDownloaded) {
            return description
        }

        val targetLang = normalizeLanguageCode(language)
        if (targetLang == "en" || language == "default") {
            return description
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetLang)
            .build()
        translator = Translation.getClient(options)

        var translatedText: String? = description
        try {
            translator?.translate(description)
                ?.addOnSuccessListener { translated ->
                    translatedText = translated
                }
                ?.addOnFailureListener { exception ->
                    LimeLog.warning("Translation failed for app $appId: ${exception.message}")
                }?.await()
        } catch (e: Exception) {
            LimeLog.warning("Translation exception for app $appId: ${e.message}")
        }

        // Save to database
        val dbHandler = GameDataBaseManager.getInstance(uiContext)
        if (dbHandler != null && dbHandler.queryDataExists(appId)) {
            val appData = dbHandler.getAppData(appId)
            appData.translatedText = translatedText
            dbHandler.updateGameEntry(appData)
        }

        return translatedText
    }
}