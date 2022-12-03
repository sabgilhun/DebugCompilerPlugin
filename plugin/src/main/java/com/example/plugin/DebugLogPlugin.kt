package com.example.plugin

import com.example.plugin.DebugLogPluginExtension.Companion.EXTENSION_NAME
import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@AutoService(KotlinCompilerPluginSupportPlugin::class)
class DebugLogPlugin : KotlinCompilerPluginSupportPlugin {

    override fun apply(target: Project) {
        super.apply(target)
        target.extensions.create(EXTENSION_NAME, DebugLogPluginExtension::class.java)
    }

    override fun isApplicable(
        kotlinCompilation: KotlinCompilation<*>,
    ) = kotlinCompilation.target.project.plugins.hasPlugin(DebugLogPlugin::class.java)

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extensions = project.extensions.getByName(EXTENSION_NAME) as DebugLogPluginExtension
        val useTimeElapsedPrintingProperty = extensions.useTimeElapsedPrinting.get()

        return project.provider {
            listOf(
                SubpluginOption("useTimeElapsedPrinting", useTimeElapsedPrintingProperty.toString())
            )
        }
    }

    override fun getCompilerPluginId() = "debuglog-compiler-plugin"

    override fun getPluginArtifact() = SubpluginArtifact(
        groupId = "com.example.plugin",
        artifactId = "debuglog-compiler-plugin",
        version = "1.0.0"
    )
}