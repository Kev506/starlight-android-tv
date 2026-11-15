package com.starlight.utils

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.crashlytics
import com.starlight.BuildConfig
import com.starlight.utils.Constants.DEFAULT_RENDERER_PREF
import com.starlight.utils.Constants.FINGERPRINT_PREF
import com.starlight.utils.Constants.GLES_PREF_STRING
import com.starlight.utils.Constants.RENDERER_PREF
import com.starlight.utils.Constants.STARLIGHT_PREF_NAME
import com.starlight.utils.Constants.STATS_ENABLED
import com.starlight.utils.Constants.STATS_PREF_STRING
import com.starlight.utils.LimeLog.info
import com.starlight.utils.LimeLog.warning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch


class Starlight : Application() {

    init {
        if (BuildConfig.DEBUG) {
            try {
                Class.forName("dalvik.system.CloseGuard")
                    .getMethod("setEnabled", Boolean::class.javaPrimitiveType)
                    .invoke(null, true)
            } catch (e: ReflectiveOperationException) {
                warning("Starlight: Failed to enable CloseGuard$e")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        LimeLog.initialize(this@Starlight)

        applicationScope.launch {
            dataStore.data
                .map { prefs -> prefs[booleanPreferencesKey(STATS_PREF_STRING)] ?: STATS_ENABLED }
                .collect { isStatsEnabled ->
                    if (isStatsEnabled) {
                        initializeFirebase()
                    } else {
                        deactivateFirebase()
                    }
                }
        }

        applicationScope.launch {
            if (isGlRendererMissing(dataStore)) {
                val gpuInfo = fetchDeviceGpuInfo()

                val activityManager =
                    getSystemService(ACTIVITY_SERVICE) as ActivityManager

                val configurationInfo = activityManager.deviceConfigurationInfo
                val glesVersion = configurationInfo.reqGlEsVersion

                dataStore.edit { preferences ->
                    preferences[stringPreferencesKey(RENDERER_PREF)] = gpuInfo.renderer
                    preferences[stringPreferencesKey(FINGERPRINT_PREF)] = Build.FINGERPRINT
                    preferences[intPreferencesKey(GLES_PREF_STRING)] = glesVersion
                }

                info("GPU_INFO: Renderer: ${gpuInfo.renderer}")
            }
        }
    }

    private suspend fun isGlRendererMissing(dataStore: DataStore<Preferences>): Boolean {
        return dataStore.data
            .map { preferences ->
                preferences[stringPreferencesKey(RENDERER_PREF)] ?: DEFAULT_RENDERER_PREF
            }
            .first()
            .isEmpty()
    }

    private fun initializeFirebase() {
        Firebase.crashlytics.isCrashlyticsCollectionEnabled = true
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(true)

        info("Firebase initialized")
    }

    private fun deactivateFirebase() {
        Firebase.crashlytics.isCrashlyticsCollectionEnabled = false
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false)

        info("Firebase deactivated")
    }

    companion object {

        lateinit var instance: Starlight
            private set

        lateinit var applicationScope: CoroutineScope
            private set

        private const val DATASTORE_NAME = STARLIGHT_PREF_NAME

        val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

        // Job and scope for quitting the game
        var quitGameJob: Job? = null

        fun isTvDevice(): Boolean {
            return instance.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        }

        fun isGoogleTV(): Boolean {
            return isTvDevice() && try {
                instance.packageManager.getPackageInfoCompat(
                    "com.google.android.apps.tv.launcherx",
                    0
                )
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

        fun isClassicAndroidTV(): Boolean {
            return isTvDevice() && !isGoogleTV()
        }

        fun isProjectivyInstalled(): Boolean {
            return try {
                instance.packageManager.getPackageInfoCompat("com.spocky.projengmenu", 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

        private fun PackageManager.getPackageInfoCompat(
            packageName: String,
            flags: Int = 0
        ): PackageInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION") getPackageInfo(packageName, flags)
            }
    }
}