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

        val logTagProperty = extensions.logTag.orNull
        val logLevelProperty = extensions.logLevel.orNull

        if (
            logLevelProperty != null
            && !listOf("verbose", "info", "debug", "warning", "error").contains(logLevelProperty)
        ) {
            throw IllegalArgumentException("logLevel property 값은 verbose, info, debug, warning, error 중 하나이여야 합니다.")
        }

        return project.provider {
            listOf(
                SubpluginOption("logTag", logTagProperty.orEmpty()),
                SubpluginOption("logLevel", logLevelProperty.orEmpty())
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