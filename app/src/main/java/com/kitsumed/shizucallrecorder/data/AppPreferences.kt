/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.data

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import androidx.core.net.toUri
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyAudioCodec
import com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyAudioSource
import com.kitsumed.shizucallrecorder.services.callDetection.CallDetectionMode
import com.kitsumed.shizucallrecorder.utils.AppLogger

/**
 * AppPreferences wraps [android.content.SharedPreferences] to provide typed access to all
 * user-configurable settings stored on the device.
 */
class AppPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "shizucallrecorder_prefs"
    }

    /**
     * Single source of truth for all default settings values.
     * These value are the default app settings.
     */
    object DefaultsValue {
        // --- Onboarding & Legal ---
        const val DISCLAIMER_ACCEPTED = false

        // Calculates (Install Time - 10 Months) to leave exactly 2 months remaining
        fun LAST_FORCED_REMINDER_SUPPORT_PROJECT_TIME(context: Context): Long = (runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime }.getOrDefault(Long.MIN_VALUE)) - 25920000000L // 300 days in milliseconds
        
        // --- Storage & General ---
        val RECORDING_FOLDER_URI: String? = null
        const val VIBRATION_ENABLED = true
        val CALL_DETECTION_MODE = CallDetectionMode.getDefaultModeForDevice().key
        const val RECORD_THIRD_PARTY_CALLS = false

        const val POST_RECORDING_FILE_ACTIONS_NOTIFICATION_ENABLED = false
        const val AUTO_RECORD_INCOMING = true
        const val AUTO_RECORD_OUTGOING = true

        
        // --- Developer & Debug ---
        const val LOGGING_ENABLED = false
        const val DEBUG_ENABLED = false
        const val DEBUG_CALLER_NUMBER = ""
        
        // --- Audio/Scrcpy Quality ---
        val AUDIO_SOURCE = ScrcpyAudioSource.VOICE_CALL.cliKey
        val AUDIO_CODEC = ScrcpyAudioCodec.OPUS.cliKey

        val AUDIO_BITRATE = ScrcpyAudioCodec.OPUS.defaultBitRate

        // --- UI & Appearance ---
        val THEME_MODE = ThemeMode.SYSTEM
        const val DYNAMIC_COLOR = true
        const val SHOW_TOASTS = true
        const val SHOW_RECORDING_OVERLAY = false
        const val OVERLAY_Y_POSITION = -1
    }

    /**
     * Enum containing all SharedPreferences keys to prevent string typos.
     * Add new keys here when adding new settings.
     */
    enum class Key(val id: String) {
        // --- Onboarding & Legal ---
        DISCLAIMER_ACCEPTED("disclaimer_accepted"),

        LAST_FORCED_REMINDER_SUPPORT_PROJECT_TIME_INAPP("last_forced_reminder_support_project_time_inapp"),
        LAST_FORCED_REMINDER_SUPPORT_PROJECT_TIME_NOTIFICATION("last_forced_reminder_support_project_time_notification"),
        // --- Others ---
        RECORDING_FOLDER_URI("recording_folder_uri"),
        VIBRATION_ENABLED("vibration_enabled"),
        POST_RECORDING_FILE_ACTIONS_NOTIFICATION_ENABLED ("post_recording_file_actions_notification_enabled"),
        AUTO_RECORD_INCOMING("auto_record_incoming"),
        AUTO_RECORD_OUTGOING("auto_record_outgoing"),
        LOGGING_ENABLED("logging_enabled"),
        DEBUG_ENABLED("debug_enabled"),
        DEBUG_CALLER_NUMBER("debug_caller_number"),
        AUDIO_SOURCE("audio_source"),
        AUDIO_CODEC("audio_codec"),
        AUDIO_BITRATE("audio_bitrate"),
        THEME_MODE("theme_mode"),
        DYNAMIC_COLOR("dynamic_color"),
        SHOW_TOASTS("show_toasts"),
        SHOW_RECORDING_OVERLAY("show_recording_overlay"),
        OVERLAY_Y_POSITION("overlay_y_position"),
        CALL_DETECTION_MODE("call_detection_mode"),
        RECORD_THIRD_PARTY_CALLS("record_third_party_calls");
    }

    // -------- Nested enums

    /**
     * Controls the app theme.
     *
     * @param key The lowercase string.
     */
    enum class ThemeMode(val key: String, val displayNameResId: Int) {
        SYSTEM("system", R.string.settings_theme_mode_system),
        LIGHT("light", R.string.settings_theme_mode_light),
        DARK("dark", R.string.settings_theme_mode_dark);
        companion object {
            /**
             * Parses a key string back into an enum constant.
             *
             * @throws IllegalArgumentException if no matching entry is found.
             * @param key The string stored in SharedPreferences.
             * @return The matching [ThemeMode], or throws an error if unrecognized.
             */
            fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: throw IllegalArgumentException("Unknown ThemeMode key: $key")
        }
    }

    // -------- SharedPreferences instance

    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // -------- Helpers to simplify reading/writing

    private fun getBoolean(key: Key, default: Boolean = false) = prefs.getBoolean(key.id, default)
    private fun setBoolean(key: Key, value: Boolean) = prefs.edit { putBoolean(key.id, value) }

    private fun getString(key: Key, default: String? = null) = prefs.getString(key.id, default)
    private fun setString(key: Key, value: String?) = prefs.edit { putString(key.id, value) }

    private fun getInt(key: Key, default: Int = 0) = prefs.getInt(key.id, default)
    private fun setInt(key: Key, value: Int) = prefs.edit { putInt(key.id, value) }

    private fun getLong(key: Key, default: Long = 0L) = prefs.getLong(key.id, default)
    private fun setLong(key: Key, value: Long) = prefs.edit { putLong(key.id, value) }

    private fun getStringSet(key: Key, default: Set<String> = emptySet()) = prefs.getStringSet(key.id, default)?.toSet().orEmpty()
    private fun setStringSet(key: Key, value: Set<String>) = prefs.edit { putStringSet(key.id, value) }

    // ==========================================
    // -------- Accessors (By Category) ---------
    // ==========================================

    // -------- Onboarding & Disclaimer --------

    /** Checks if the user has accepted the disclaimer. */
    fun isDisclaimerAccepted() = getBoolean(Key.DISCLAIMER_ACCEPTED, DefaultsValue.DISCLAIMER_ACCEPTED)
    
    /** Sets whether the user has accepted the disclaimer. */
    fun setDisclaimerAccepted(accepted: Boolean) = setBoolean(Key.DISCLAIMER_ACCEPTED, accepted)

    /** Gets the timestamp of the last forced reminder about support project shown in-app. */
    fun getLastForcedReminderSupportProjectTimeInApp() = getLong(Key.LAST_FORCED_REMINDER_SUPPORT_PROJECT_TIME_INAPP, DefaultsValue.LAST_FORCED_REMINDER_SUPPORT_PROJECT_TIME(appContext))

    /** Sets the timestamp of the last forced reminder about support project shown in-app. */
    fun setLastForcedReminderSupportProjectTimeInApp(time: Long) = setLong(Key.LAST_FORCED_REMINDER_SUPPORT_PROJECT_TIME_INAPP, time)

    /** Gets the timestamp of the last forced reminder about support project shown in notifications. */
    fun getLastForcedReminderSupportProjectTimeNotification() = getLong(Key.LAST_FORCED_REMINDER_SUPPORT_PROJECT_TIME_NOTIFICATION, DefaultsValue.LAST_FORCED_REMINDER_SUPPORT_PROJECT_TIME(appContext))

    /** Sets the timestamp of the last forced reminder about support project shown in notifications. */
    fun setLastForcedReminderSupportProjectTimeNotification(time: Long) = setLong(Key.LAST_FORCED_REMINDER_SUPPORT_PROJECT_TIME_NOTIFICATION, time)

    // -------- Storage & General --------

    /** Gets the user-selected folder URI for storing recordings. */
    fun getRecordingFolderUri(): Uri? = getString(Key.RECORDING_FOLDER_URI, DefaultsValue.RECORDING_FOLDER_URI)?.toUri()
    
    /** Sets the user-selected folder URI for storing recordings. */
    fun setRecordingFolderUri(uri: Uri?) = setString(Key.RECORDING_FOLDER_URI, uri?.toString())

    /** Checks if vibration is enabled for notifications/actions. */
    fun isVibrationEnabled() = getBoolean(Key.VIBRATION_ENABLED, DefaultsValue.VIBRATION_ENABLED)
    
    /** Sets whether vibration is enabled. */
    fun setVibrationEnabled(enabled: Boolean) = setBoolean(Key.VIBRATION_ENABLED, enabled)

    /** Checks if post-recording file actions notification is enabled. */
    fun isPostRecordingFileActionsNotificationEnabled() = getBoolean(Key.POST_RECORDING_FILE_ACTIONS_NOTIFICATION_ENABLED, DefaultsValue.POST_RECORDING_FILE_ACTIONS_NOTIFICATION_ENABLED)
    /** Sets whether post-recording file actions notification is enabled. */
    fun setPostRecordingFileActionsNotificationEnabled(enabled: Boolean) = setBoolean(Key.POST_RECORDING_FILE_ACTIONS_NOTIFICATION_ENABLED, enabled)

    /**
     * Gets the current preferred detection mode, automatically falling back
     * to a supported system mode if the saved preference is illegal for the current API.
     */
    fun getCallDetectionMode(): CallDetectionMode {
        val savedKey = getString(Key.CALL_DETECTION_MODE, DefaultsValue.CALL_DETECTION_MODE)
        val savedMode = try {
            CallDetectionMode.fromKey(savedKey)
        } catch (e: IllegalArgumentException) {
            AppLogger.e( "Invalid saved CallDetectionMode key: $savedKey, falling back to default. Error: ${e.message}")
            CallDetectionMode.getDefaultModeForDevice()
        }

        // Safety fallback for API compatibility mismatches, for example if user update or downgrade Android version
        return if (savedMode.isSupportedOnCurrentApi()) {
            savedMode
        } else {
            AppLogger.w( "Saved CallDetectionMode ${savedMode.key} is not supported on current API level, falling back to default.")
            CallDetectionMode.getDefaultModeForDevice()
        }
    }

    /**
     * Sets the call direction mode.
     * @throws IllegalArgumentException if the provided mode is not supported on the current API level, to prevent saving an invalid preference.
     */
    fun setCallDetectionMode(mode: CallDetectionMode) {
        if (!mode.isSupportedOnCurrentApi()) {
            throw IllegalArgumentException("Mode ${mode.name} is not supported on API ${Build.VERSION.SDK_INT}")
        }
        setString(Key.CALL_DETECTION_MODE, mode.key)
    }

    /** Checks if recording of calls from third-party apps (e.g. WhatsApp, Signal) is enabled. */
    fun isRecordThirdPartyCallsEnabled() = getBoolean(Key.RECORD_THIRD_PARTY_CALLS, DefaultsValue.RECORD_THIRD_PARTY_CALLS)
    /** Sets whether recording of calls from third-party apps is enabled. */
    fun setRecordThirdPartyCallsEnabled(enabled: Boolean) = setBoolean(Key.RECORD_THIRD_PARTY_CALLS, enabled)

    // -------- Automation --------

    /** Checks if auto-recording for incoming calls is enabled. */
    fun isAutoRecordIncomingEnabled() = getBoolean(Key.AUTO_RECORD_INCOMING, DefaultsValue.AUTO_RECORD_INCOMING)
    
    /** Sets whether auto-recording for incoming calls is enabled. */
    fun setAutoRecordIncomingEnabled(enabled: Boolean) = setBoolean(Key.AUTO_RECORD_INCOMING, enabled)

    /** Checks if auto-recording for outgoing calls is enabled. */
    fun isAutoRecordOutgoingEnabled() = getBoolean(Key.AUTO_RECORD_OUTGOING, DefaultsValue.AUTO_RECORD_OUTGOING)
    
    /** Sets whether auto-recording for outgoing calls is enabled. */
    fun setAutoRecordOutgoingEnabled(enabled: Boolean) = setBoolean(Key.AUTO_RECORD_OUTGOING, enabled)

    // -------- Debug --------

    /** Checks if logging features are enabled. */
    fun isLoggingEnabled() = getBoolean(Key.LOGGING_ENABLED, DefaultsValue.LOGGING_ENABLED)

    /** Sets whether logging features are enabled. */
    fun setLoggingEnabled(enabled: Boolean) = setBoolean(Key.LOGGING_ENABLED, enabled)

    /** Checks if debug features are enabled. */
    fun isDebugEnabled() = getBoolean(Key.DEBUG_ENABLED, DefaultsValue.DEBUG_ENABLED)
    
    /** Sets whether debug features are enabled. */
    fun setDebugEnabled(enabled: Boolean) = setBoolean(Key.DEBUG_ENABLED, enabled)

    /** Gets the caller number override used for debugging. */
    fun getDebugCallerNumber() = getString(Key.DEBUG_CALLER_NUMBER, DefaultsValue.DEBUG_CALLER_NUMBER) ?: DefaultsValue.DEBUG_CALLER_NUMBER
    
    /** Sets the caller number override used for debugging. */
    fun setDebugCallerNumber(number: String) = setString(Key.DEBUG_CALLER_NUMBER, number)

    // -------- Audio/Scrcpy Quality --------

    /** Gets the configured audio source for scrcpy integration. */
    fun getAudioSource() = getString(Key.AUDIO_SOURCE, DefaultsValue.AUDIO_SOURCE) ?: DefaultsValue.AUDIO_SOURCE
    
    /** Sets the configured audio source. */
    fun setAudioSource(source: String) = setString(Key.AUDIO_SOURCE, source)

    /** Gets the configured audio codec for scrcpy integration. */
    fun getAudioCodec() = getString(Key.AUDIO_CODEC, DefaultsValue.AUDIO_CODEC) ?: DefaultsValue.AUDIO_CODEC
    
    /** Sets the configured audio codec. */
    fun setAudioCodec(codec: String) = setString(Key.AUDIO_CODEC, codec)

    /** Gets the configured audio bitrate. */
    fun getAudioBitRate() = getInt(Key.AUDIO_BITRATE, DefaultsValue.AUDIO_BITRATE)

    /** Sets the configured audio bitrate. */
    fun setAudioBitRate(bitRate: Int) = setInt(Key.AUDIO_BITRATE, bitRate)

    // -------- UI & Appearance --------

    /** Gets the current UI theme mode. */
    fun getThemeMode() = ThemeMode.fromKey(getString(Key.THEME_MODE, DefaultsValue.THEME_MODE.key))
    
    /** Sets the current UI theme mode. */
    fun setThemeMode(mode: ThemeMode) = setString(Key.THEME_MODE, mode.key)

    /** Checks if dynamic color (Material You) is enabled. */
    fun isDynamicColorEnabled() = getBoolean(Key.DYNAMIC_COLOR, DefaultsValue.DYNAMIC_COLOR)
    
    /** Sets whether dynamic color is enabled. */
    fun setDynamicColorEnabled(enabled: Boolean) = setBoolean(Key.DYNAMIC_COLOR, enabled)

    /** Checks if toast notifications are enabled. */
    fun isShowToastsEnabled() = getBoolean(Key.SHOW_TOASTS, DefaultsValue.SHOW_TOASTS)

    /** Sets whether toast notifications are enabled. */
    fun setShowToastsEnabled(enabled: Boolean) = setBoolean(Key.SHOW_TOASTS, enabled)

    /** Checks if the recording overlay is enabled. */
    fun isOverlayEnabled() = getBoolean(Key.SHOW_RECORDING_OVERLAY, DefaultsValue.SHOW_RECORDING_OVERLAY)

    /** Sets whether the recording overlay is enabled. */
    fun setOverlayEnabled(enabled: Boolean) = setBoolean(Key.SHOW_RECORDING_OVERLAY, enabled)

    /** Gets the Y position of the recording overlay. */
    fun getOverlayYPosition() = getInt(Key.OVERLAY_Y_POSITION, DefaultsValue.OVERLAY_Y_POSITION)

    /** Sets the Y position of the recording overlay. */
    fun setOverlayYPosition(y: Int) = setInt(Key.OVERLAY_Y_POSITION, y)


}
