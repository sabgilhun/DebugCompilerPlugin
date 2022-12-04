package com.example.processor

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

@AutoService(CommandLineProcessor::class)
class DebugLogCommandLineProcessor : CommandLineProcessor {

    override val pluginId = "debuglog-compiler-plugin"

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

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            "logTag" -> configuration.put(argLogTag, value)
            "argLogLevel" -> configuration.put(argLogLevel, value)
        }
    }

    companion object {
        val argLogTag = CompilerConfigurationKey<String>("logTag")
        val argLogLevel = CompilerConfigurationKey<String>("logLevel")
    }
}