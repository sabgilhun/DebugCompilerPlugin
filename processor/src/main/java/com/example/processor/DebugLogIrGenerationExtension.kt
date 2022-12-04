package com.example.processor

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.name.FqName

class DebugLogIrGenerationExtension(
    private val messageCollector: MessageCollector,
    private val useTimeElapsedPrinting: Boolean,
) : IrGenerationExtension {

    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        messageCollector.report(CompilerMessageSeverity.INFO, "start generating ir step")

        val debugLogTransformer = DebugLogTransformer(
            messageCollector,
            useTimeElapsedPrinting,
            pluginContext
        )

        moduleFragment.transform(debugLogTransformer, null)
    }
}