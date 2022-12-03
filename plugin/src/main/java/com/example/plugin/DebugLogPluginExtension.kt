package com.example.plugin

import org.gradle.api.provider.Property

interface DebugLogPluginExtension {
    val useTimeElapsedPrinting: Property<Boolean>

    companion object {
        const val EXTENSION_NAME = "debuglog"
    }
}