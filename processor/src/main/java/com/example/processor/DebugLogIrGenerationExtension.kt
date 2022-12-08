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

    /** nav-13
     * Compiler 단계에서 IR generate 된 후 호출 됨
     * moduleFragment < IR root node 객체
     * (IR도 Tree 형태)
     * pluginContext < symbol 정보 담고 있는 객체
     **/
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        messageCollector.report(CompilerMessageSeverity.INFO, "start generating ir step")

        /** nav-14
         * IR tree 를 순회하기 위한 Transformer(Visitor)
         * 이 내부에서 코드 수정 이뤄짐
         **/
        val debugLogTransformer =
            DebugLogTransformer(messageCollector, pluginContext, logTag, logLevel)

        moduleFragment.transform(debugLogTransformer, null)
    }
}