/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 * Copyright (C) 2026-present kitsumed (Med)
 * Licensed under the GNU General Public License v3 or later.
 */
package com.kitsumed.shizucallrecorder.utils

import android.content.Context
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.call.EnrichedCallData
import com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyAudioCodec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Creates permanent recording filenames in this order:
 * date_time_caller_name_direction_phone_number.extension
 *
 * Example: 2026-10-17_12-34-56_John_Doe_Incoming_+2301234567.opus
 */
object RecordingFileNameFormatter {
    private val invalidFileNameChars = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

    private fun safePart(value: String, fallback: String): String {
        val safe = value.trim()
            .replace(invalidFileNameChars, "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '.', ' ')
        return safe.ifBlank { fallback }
    }

    fun formatFileName(
        context: Context,
        metadata: EnrichedCallData,
        codec: ScrcpyAudioCodec,
        customFormat: String? = null
    ): String {
        // Retain the parameter for binary/source compatibility, but no longer allow custom templates.
        @Suppress("UNUSED_VARIABLE") val ignoredCustomFormat = customFormat
        @Suppress("UNUSED_VARIABLE") val ignoredContext = context
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
        val callerName = safePart(metadata.callerName.orEmpty(), "Unknown_Caller")
        val direction = when (metadata.direction) {
            CallDirection.INCOMING -> "Incoming"
            CallDirection.OUTGOING -> "Outgoing"
        }
        val phoneNumber = safePart(metadata.getBestNumber(), "Unknown_Number")
        val baseName = listOf(timestamp, callerName, direction, phoneNumber).joinToString("_")
        AppLogger.v("Created fixed-format recording filename: '$baseName'")
        return "$baseName${codec.containerExtension}"
    }
}
