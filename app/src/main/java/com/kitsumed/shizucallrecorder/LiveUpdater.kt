package com.kitsumed.shizucallrecorder

import android.app.Activity
import android.content.Context
import android.os.Build
import com.kitsumed.shizucallrecorder.services.recording.RecordingForegroundService
import dev.n2k02.updater.PublishedUpdateSource
import dev.n2k02.updater.UpdateConfig
import dev.n2k02.updater.UpdateInstallGuard
import dev.n2k02.updater.UpdateStatus
import dev.n2k02.updater.UpdaterController
import kotlinx.coroutines.flow.MutableStateFlow

/** Live shared updater for ShizuCallRecorder's stable and experimental channels. */
internal object LiveUpdater {
    private lateinit var app: Context
    private var instance: UpdaterController? = null
    val experimentalEnabled = MutableStateFlow(false)
    val installGuard = UpdateInstallGuard { !RecordingForegroundService.activeForUpdate }

    fun init(context: Context) {
        app = context.applicationContext
        experimentalEnabled.value = prefs().getBoolean("experimentalEnabled", false)
        instance = create()
    }

    private fun prefs() = app.getSharedPreferences("app_updater", Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    private fun versionCode(): Long {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    val installedVersionName: String
        @Suppress("DEPRECATION")
        get() = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown"

    private fun create() = UpdaterController(
        context = app,
        config = UpdateConfig(
            repository = "N2K02/ShizuCallRecorder",
            packageName = app.packageName,
            installedVersionCode = versionCode(),
            experimentalEnabled = experimentalEnabled.value,
        ),
        publishedSource = PublishedUpdateSource("https://n2k02.github.io/shizucallrecorder/updates"),
        offerPreferences = prefs(),
    )

    val controller: UpdaterController
        get() = requireNotNull(instance) { "LiveUpdater.init must be called first" }

    fun setExperimentalEnabled(enabled: Boolean) {
        if (experimentalEnabled.value == enabled) return
        controller.cancel()
        prefs().edit().putBoolean("experimentalEnabled", enabled).apply()
        experimentalEnabled.value = enabled
        instance = create()
        controller.check(manual = true)
    }

    fun onResume(activity: Activity) {
        when (controller.status.value) {
            is UpdateStatus.NeedsInstallPermission -> controller.install(activity, installGuard)
            UpdateStatus.Idle -> controller.check(manual = false)
            else -> Unit
        }
    }
}
