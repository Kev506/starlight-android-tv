package com.starlight.ui.dialogs

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.starlight.R
import com.starlight.activities.MainView
import com.starlight.games.GameDataBaseManager
import com.starlight.models.MainViewModel
import com.starlight.nvstream.http.PairingManager
import com.starlight.preferences.PreferenceKeys.dataStoreConfigFlow
import com.starlight.utils.Constants.DATABASE_PREF
import com.starlight.utils.Constants.ML_DOWNLOADED
import com.starlight.utils.LimeLog
import com.starlight.utils.LimeLog.severe
import com.starlight.utils.Starlight.Companion.applicationScope
import com.starlight.utils.Starlight.Companion.dataStore
import com.starlight.utils.normalizeLanguageCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Created by Kevin Andrews on 07/03/2026.
 */

@Composable
fun DownloadDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    icon: Int?,
    language: String,
    viewModel: MainViewModel
) {

    val activity = LocalActivity.current as AppCompatActivity
    val progressValue by viewModel.downloadProgress.collectAsState()
    val context = LocalContext.current

    val config by context.dataStoreConfigFlow.collectAsStateWithLifecycle(initialValue = null)

    config?.let { currentConfig ->

        val targetLang = normalizeLanguageCode(language)
        val options = remember(targetLang) {
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(targetLang)
                .build()
        }
        val translator = remember(options) { Translation.getClient(options) }

        LaunchedEffect(targetLang) {
            downloadModel(
                activity = activity,
                translator = translator,
                viewModel = viewModel,
                language = targetLang
            )
        }

        val animatedProgress by animateFloatAsState(
            targetValue = progressValue,
            animationSpec = tween(
                durationMillis = 200,
                easing = LinearEasing
            ),
            label = "DownloadProgressAnimation"
        )

        LaunchedEffect(key1 = progressValue) {
            if (progressValue >= 1f) {
                LimeLog.info("Download complete, closing dialog")
                delay(2000.milliseconds)
                viewModel.setLoaderValue(false)
            }
        }

        Dialog(
            onDismissRequest = {
                onDismissRequest()
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {

            Box(
                modifier = Modifier
                    .background(Color(0xFF026D91), RoundedCornerShape(12.dp))
                    .padding(24.dp)
            ) {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .focusProperties { canFocus = false },
                        horizontalArrangement = Arrangement.Center
                    ) {
                        icon?.let {
                            Image(
                                painter = painterResource(it),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.padding(5.dp))

                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = message,
                        fontSize = 14.sp,
                        color = White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        strokeCap = StrokeCap.Round,
                        color = Color(0xFF33B5E5),
                        trackColor = White,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = activity.getString(R.string.downloading_ml_language_file_please_wait) + " " + "${(animatedProgress * 100).toInt()}%",
                        fontSize = 16.sp,
                        color = White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.wrapContentSize()
                    )
                }
            }
        }
    }
}

// Download the language model
private suspend fun downloadModel(
    activity: AppCompatActivity,
    translator: Translator?,
    viewModel: MainViewModel,
    language: String
) {
    if (language == "default" || language == "en") {
        viewModel.setLoaderValue(false)
        return
    }

    val conditions = DownloadConditions.Builder().build()
    viewModel.updateDownloadProgress(0.1f)

    val progressJob = applicationScope.launch {
        var currentProgress = 0.1f
        while (currentProgress < 0.9f) {
            delay(300.milliseconds)
            currentProgress += 0.05f
            viewModel.updateDownloadProgress(currentProgress.coerceAtMost(0.9f))
        }
    }

    try {
        translator?.downloadModelIfNeeded(conditions)?.await()

        progressJob.cancel()
        viewModel.updateDownloadProgress(1.0f)

        activity.dataStore.edit { preferences ->
            preferences[booleanPreferencesKey(ML_DOWNLOADED)] = true
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(
                activity,
                activity.getString(R.string.download_successful), Toast.LENGTH_SHORT
            ).show()
        }

        if (viewModel.computer?.pairState == PairingManager.PairState.PAIRED) {
            val dbManager =
                GameDataBaseManager.getInstance(activity)
            dbManager?.deleteGameData(
                activity,
                dbManager.writableDatabase
            )

            activity.dataStore.edit { preferences ->
                preferences[booleanPreferencesKey(DATABASE_PREF)] =
                    false
            }

            viewModel.isDatabaseCleared = true
            val intent =
                Intent(activity, MainView::class.java).apply {
                    val bundle = Bundle()
                    bundle.putBoolean(
                        "DATABASE_CLEARED",
                        viewModel.isDatabaseCleared
                    )
                    putExtra("appBundle", bundle)
                }.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            activity.startActivity(intent)
            activity.finish()
        }

    } catch (exception: Exception) {
        progressJob.cancel()
        activity.dataStore.edit { preferences ->
            preferences[booleanPreferencesKey(ML_DOWNLOADED)] = false
        }
        viewModel.updateDownloadProgress(0f)
        viewModel.setLoaderValue(false)

        withContext(Dispatchers.Main) {
            Toast.makeText(
                activity,
                activity.getString(R.string.download_error_please_try_again), Toast.LENGTH_LONG
            ).show()
        }

        val rootCause = exception.cause ?: exception
        val errorCode = (rootCause as? MlKitException)?.errorCode

        severe("MLKitTranslate: Error code: $errorCode, Message: ${rootCause.message}, $exception")
    }
}
