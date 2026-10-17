/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.services

import android.content.Context
import android.content.Intent
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.call.EnrichedCallData
import com.kitsumed.shizucallrecorder.data.call.RawCallData
import com.kitsumed.shizucallrecorder.services.recording.RecordingForegroundService
import com.kitsumed.shizucallrecorder.utils.AppLogger
import com.kitsumed.shizucallrecorder.utils.SponsorNotificationHelper

/**
 * RecordingDecisionEngine is responsible for making autonomous decisions about call recording.
 *
 * Every call detections methods must use this class to initiate the [RecordingForegroundService].
 *
 * It transforms raw call data into enriched metadata, evaluates user preferences and ignore lists,
 * and fires appropriate intents to the [RecordingForegroundService] to start recording or enter standby mode.
 *
 * @see RecordingForegroundService
 * @see EnrichedCallData
 */
class RecordingDecisionEngine private constructor(context: Context) {

    companion object {

        // Singleton instance management
        @Volatile
        private var INSTANCE: RecordingDecisionEngine? = null

        /**
         * Provides a thread-safe singleton instance of [RecordingDecisionEngine].
         * The first call initializes the instance with the provided context,
         * and subsequent calls return the same instance.
         */
        fun getInstance(context: Context): RecordingDecisionEngine {
            return INSTANCE ?: synchronized(this) {
                // We use applicationContext to avoid accidentally leaking an Activity or Service context, causing memory leaks.
                val safeContext = context.applicationContext
                INSTANCE ?: RecordingDecisionEngine(safeContext).also { INSTANCE = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val appPreferences = AppPreferences(appContext)

    init {
        AppLogger.d( "RecordingDecisionEngine initialised")
    }

    /**
     * Executes the recording decision pipeline for incoming raw call data.
     *
     * This method orchestrates the full flow:
     * 1. Transforms RawCallData into EnrichedCallData (sanitize number, format E164, check cross-country)
     * 2. Evaluates shouldAutoRecord() and ignore lists using the enriched data
     * 3. Fires the appropriate Intent to [com.kitsumed.shizucallrecorder.services.recording.RecordingForegroundService]:
     *    - [com.kitsumed.shizucallrecorder.services.recording.RecordingForegroundService.Companion.ACTION_START_RECORDING] if auto-record is enabled
     *    - [com.kitsumed.shizucallrecorder.services.recording.RecordingForegroundService.Companion.ACTION_STANDBY] otherwise
     *
     * @param rawData The [RawCallData] received from the OS or third-party source
     * @return true is an Intent to start/standby the [com.kitsumed.shizucallrecorder.services.recording.RecordingForegroundService] was fired successfully, false if there was an error
     */
    suspend fun executeDecisionPipeline(rawData: RawCallData): Boolean {
        AppLogger.i( "Starting recording decision pipeline for ${rawData.direction} call")

        // Step 1: Transform to EnrichedCallData
        val enrichedData = EnrichedCallData.enrichMetadata(appContext, rawData)
        AppLogger.d( "Enriched call data: BestNumber=${enrichedData.getBestNumber()}, crossCountry=${enrichedData.isCrossCountry}")

        // Step 2: Evaluate recording decision
        val shouldAutoRecord = shouldAutoRecord(enrichedData)
        AppLogger.i( "Recording decision for ${enrichedData.direction} call is: shouldAutoRecord=$shouldAutoRecord")

        // Step 3: Fire appropriate Intent
        return fireRecordingServiceIntent(enrichedData, shouldAutoRecord)
    }

    /**
     * Ends the current recording session, firing the STOP intent to [com.kitsumed.shizucallrecorder.services.recording.RecordingForegroundService].
     */
    fun endRecordingSession() {
        AppLogger.d( "Ending recording session. Sending STOP INTENT to RecordingForegroundService.")

        val intent = Intent(appContext, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_STOP_RECORDING
        }
        appContext.startService(intent)
        checkAndTriggerSupportNotification();
    }

    /**
     * Automatic recording is controlled only by the incoming/outgoing switches.
     * No caller, country, or contact-based ignore rules are applied.
     */
    private fun shouldAutoRecord(metadata: EnrichedCallData): Boolean =
        when (metadata.direction) {
            CallDirection.INCOMING -> appPreferences.isAutoRecordIncomingEnabled()
            CallDirection.OUTGOING -> appPreferences.isAutoRecordOutgoingEnabled()
        }

    /**
     * Fires an Intent to [RecordingForegroundService] with the appropriate action.
     *
     * @param enrichedData The enriched call data to attach to the intent
     * @param shouldAutoRecord If true, fires ACTION_START_RECORDING; otherwise ACTION_STANDBY
     * @return true if the intent was fired successfully, false if there was an error
     */
    private fun fireRecordingServiceIntent(enrichedData: EnrichedCallData, shouldAutoRecord: Boolean): Boolean {
        val action = if (shouldAutoRecord) {
            AppLogger.i( "Firing Intent: ACTION_START_RECORDING for ${enrichedData.direction} call")
            RecordingForegroundService.ACTION_START_RECORDING
        } else {
            AppLogger.i( "Firing Intent: ACTION_STANDBY for ${enrichedData.direction} call")
            RecordingForegroundService.ACTION_STANDBY
        }

        val intent = Intent(appContext, RecordingForegroundService::class.java).apply {
            this.action = action
            putExtra(EnrichedCallData.EXTRA_METADATA, enrichedData)
        }

        try {
            appContext.startForegroundService(intent)
            AppLogger.d( "Intent fired successfully")
            return true
        } catch (e: Exception) {
            AppLogger.e( "Failed to fire intent to RecordingForegroundService", e)
        }
        return false
    }

    /**
     * Checks if it has been more than 1 year since the last support us notification reminder, and posts a notification.
     */
    private fun checkAndTriggerSupportNotification() {
        val lastNotificationTime = appPreferences.getLastForcedReminderSupportProjectTimeNotification()
        val currentTime = System.currentTimeMillis()
        // 31536000000 milliseconds = 1 year
        if (currentTime - lastNotificationTime > 31536000000L) {
            AppLogger.i( "Time threshold exceeded. Displaying project support notification.")
            // Invoke your notification helper to push a standard background alert
            SponsorNotificationHelper.showSupportReminderNotification(appContext)
            // Set the preference to the current timestamp to restart the countdown timer
            appPreferences.setLastForcedReminderSupportProjectTimeNotification(currentTime)
        }
    }
}