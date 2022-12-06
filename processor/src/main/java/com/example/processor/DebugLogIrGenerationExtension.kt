package com.example.processor

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.FqName

class DebugLogIrGenerationExtension(
    private val messageCollector: MessageCollector,
    private val logTag: String,
    private val logLevel: String,
) : IrGenerationExtension {

    /**
     * fun origin() {
     *  Log.i(tag, "function name: fun-name")
     *  Log.i(tag, "params: [param1-name = ${param1}, param2-name = ${param2} ...]")
     *  val start = System.currentTimeMillis()
     *  try {
     *   < origin function body >
     *   val result = <original return>
     *   Log.i(tag, "result: ...")
     *   return ...
     *  } catch (e: Throwable) {
     *   Log.i(tag, "throw error: ...")
     *   throw e
     *  } finally {
     *   val end = System.currentTimeMillis()
     *   val elapsed = end - start
     *   Log.i(tag, "time elapsed: ${elapsed}ms")
     *  }
     * }
     */
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        messageCollector.report(CompilerMessageSeverity.INFO, "start generating ir step")

        val debugLogTransformer =
            DebugLogTransformer(messageCollector, pluginContext, logTag, logLevel)

        moduleFragment.transform(debugLogTransformer, null)
    }
}