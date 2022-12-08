package com.example.processor

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

@AutoService(ComponentRegistrar::class)
class DebugLogComponentRegistrar : ComponentRegistrar {

    /** nav-12
     * 설정 값 꺼내고, Extension 등록 및 설정 값 전달
     **/
    override fun registerProjectComponents(
        project: MockProject,
        configuration: CompilerConfiguration,
    ) {
        val messageCollector =
            configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)

        val logTag =
            configuration.get(DebugLogCommandLineProcessor.argLogTag, "DebugLog")

        val logLevel =
            configuration.get(DebugLogCommandLineProcessor.argLogLevel, "info")

        IrGenerationExtension.registerExtension(
            project,
            DebugLogIrGenerationExtension(messageCollector, logTag, logLevel)
        )
    }
}