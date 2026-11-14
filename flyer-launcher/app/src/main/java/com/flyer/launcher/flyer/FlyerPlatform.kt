package com.flyer.launcher.flyer

import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import com.flyer.launcher.core.model.Surface
import com.flyer.launcher.launcher.overlays.WidgetChoice

/**
 * Activity-backed services Flyer needs (system pickers and results). Implemented by the launcher
 * activity; Flyer screens only see this interface.
 */
interface FlyerPlatform {
    fun pickImage(onResult: (Uri?) -> Unit)
    fun pickFont(onResult: (Uri?) -> Unit)
    fun createDocument(fileName: String, onResult: (Uri?) -> Unit)
    fun openDocument(onResult: (Uri?) -> Unit)
    fun writeText(uri: Uri, text: String): Boolean
    fun readText(uri: Uri): String?
    fun isDefaultLauncher(): Boolean
    fun requestDefaultLauncher()
    fun openAccessibilitySettings()
    fun isAccessibilityHelperEnabled(): Boolean
    fun openWallpaperPicker()

    /** Starts the widget add flow (bind permission, configuration); [onAdded] receives the new widget id. */
    fun addWidget(choice: WidgetChoice, surface: Surface, onAdded: (com.flyer.launcher.core.model.WorkspaceItem) -> Unit)
}

object NoOpFlyerPlatform : FlyerPlatform {
    override fun pickImage(onResult: (Uri?) -> Unit) = onResult(null)
    override fun pickFont(onResult: (Uri?) -> Unit) = onResult(null)
    override fun createDocument(fileName: String, onResult: (Uri?) -> Unit) = onResult(null)
    override fun openDocument(onResult: (Uri?) -> Unit) = onResult(null)
    override fun writeText(uri: Uri, text: String) = false
    override fun readText(uri: Uri): String? = null
    override fun isDefaultLauncher() = false
    override fun requestDefaultLauncher() = Unit
    override fun openAccessibilitySettings() = Unit
    override fun isAccessibilityHelperEnabled() = false
    override fun openWallpaperPicker() = Unit
    override fun addWidget(choice: WidgetChoice, surface: Surface, onAdded: (com.flyer.launcher.core.model.WorkspaceItem) -> Unit) = Unit
}

val LocalFlyerPlatform = staticCompositionLocalOf<FlyerPlatform> { NoOpFlyerPlatform }
