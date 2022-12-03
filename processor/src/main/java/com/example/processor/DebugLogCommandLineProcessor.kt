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
            optionName = "useTimeElapsedPrinting",
            valueDescription = "Boolean",
            description = "option for using time elapsed print feature",
            required = true,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        if (option.optionName == "useTimeElapsedPrinting") {
            configuration.put(argUseTimeElapsedPrinting, value.toBoolean())
        }
    }

    companion object {
        val argUseTimeElapsedPrinting = CompilerConfigurationKey<Boolean>("useTimeElapsedPrinting")
    }
}