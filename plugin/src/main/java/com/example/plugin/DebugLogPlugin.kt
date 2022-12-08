package com.example.plugin

import com.example.plugin.DebugLogPluginExtension.Companion.EXTENSION_NAME
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/** nav-1
 *  Gradle Plugin, Subplugin 구현
 *  KotlinCompilerPluginSupportPlugin 의 구현 해야할 함수만 구현하면,
 *  Gradle Plugin - Kotlin Compiler Plugin 연결
**/
class DebugLogPlugin : KotlinCompilerPluginSupportPlugin {

    /** nav-2
     *  Gradle Extension 등록
     **/
    override fun apply(target: Project) {
        super.apply(target)
        target.extensions.create(EXTENSION_NAME, DebugLogPluginExtension::class.java)
    }

    /** nav-4
     *  정확히 왜 있는지 모름
     *  build variants 분기용?으로 보임
     *  다른 프로젝트들은 그냥 'true' 로 고정해서 쓰기도 함
     **/
    override fun isApplicable(
        kotlinCompilation: KotlinCompilation<*>,
    ) = kotlinCompilation.target.project.plugins.hasPlugin(DebugLogPlugin::class.java)

    /** nav-5
     *  Gradle Plugin Extension 으로 받은 설정값들 Subplugin 으로 전달
     **/
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

    /** nav-6
     *  Kotlin Compiler Plugin id 등록
     **/
    override fun getCompilerPluginId() = "debuglog-compiler-plugin"

    /** nav-7
     *  Kotlin Compiler Plugin artifact 등록
     **/
    override fun getPluginArtifact() = SubpluginArtifact(
        groupId = "com.example.plugin",
        artifactId = "debuglog-compiler-plugin",
        version = "1.0.0"
    )
}