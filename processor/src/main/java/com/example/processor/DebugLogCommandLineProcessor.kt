package com.example.processor

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

@AutoService(CommandLineProcessor::class)
class DebugLogCommandLineProcessor : CommandLineProcessor {

    /** nav-9
     *  KotlinCompilerPluginSupportPlugin 에서 지정했던 id 써야 함
     **/
    override val pluginId = "debuglog-compiler-plugin"

    /** nav-10
     * Kotlin Compiler Plugin 에서 사용하는 옵션 정의
     **/
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = "logTag",
            valueDescription = "String",
            description = "Logging시 사용되는 Tag값",
            required = false,
        ),
        CliOption(
            optionName = "logLevel",
            valueDescription = "String",
            description = "Logging시 적용되는 Level값",
            required = false,
        ),
    )

    /** nav-11
     * SubPlugin, Command Line 을 통해 들어온 옵션, 등록
     **/
    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            "logTag" -> if (value.isNotEmpty()) {
                configuration.put(argLogTag, value)
            }
            "argLogLevel" -> if (value.isNotEmpty()) {
                configuration.put(argLogLevel, value)
            }
        }
    }

    companion object {
        val argLogTag = CompilerConfigurationKey<String>("logTag")
        val argLogLevel = CompilerConfigurationKey<String>("logLevel")
    }
}