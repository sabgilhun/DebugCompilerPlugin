package com.example.plugin

import org.gradle.api.provider.Property

interface DebugLogPluginExtension {

    val logTag: Property<String>

    /**
     * verbose: v
     * info: i
     * debug: d
     * warning: w
     * error: e
     */
    val logLevel: Property<String>

    companion object {
        const val EXTENSION_NAME = "debugLog"
    }
}