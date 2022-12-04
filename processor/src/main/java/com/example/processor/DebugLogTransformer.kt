package com.example.processor

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrTryImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.name.FqName

class DebugLogTransformer(
    private val messageCollector: MessageCollector,
    private val useTimeElapsedPrinting: Boolean,
    private val pluginContext: IrPluginContext,
) : IrElementTransformerVoidWithContext() {

    private val typeUnit = pluginContext.irBuiltIns.unitType

    private val typeThrowable = pluginContext.irBuiltIns.throwableType

    private val debugLogAnnotationIrReference = findAnnotationIrReference(pluginContext)

    private val printlnFunIrReference = findPrintlnFunIrReference(pluginContext)

    private val monotonicClassIrReference = findMonotonicClassIrReference()

    private val markNowFunIrReference = findMarkNowFunIrReference()

    private val elapsedNowFunIrReference = findElapsedNowFunIrReference()

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        val body = declaration.body
        if (body != null && declaration.hasAnnotation(debugLogAnnotationIrReference)) {
            declaration.body = irDebug(declaration, body)
            messageCollector.report(
                CompilerMessageSeverity.INFO,
                "--generated fun--\n" + declaration.body?.dump()
            )
        }
        return super.visitFunctionNew(declaration)
    }

    private fun findAnnotationIrReference(
        pluginContext: IrPluginContext,
    ): IrClassSymbol =
        checkNotNull(pluginContext.referenceClass(FqName("com.example.annotation.DebugLog")))

    private fun findPrintlnFunIrReference(
        pluginContext: IrPluginContext,
    ): IrSimpleFunctionSymbol {
        val typeAnyNullable = pluginContext.irBuiltIns.anyNType
        return pluginContext.referenceFunctions(FqName("kotlin.io.println"))
            .single {
                val parameters = it.owner.valueParameters
                parameters.size == 1 && parameters[0].type == typeAnyNullable
            }
    }

    private fun findMonotonicClassIrReference(): IrClassSymbol =
        checkNotNull(pluginContext.referenceClass(FqName("kotlin.time.TimeSource.Monotonic")))

    private fun findMarkNowFunIrReference(): IrSimpleFunctionSymbol =
        pluginContext.referenceFunctions(FqName("kotlin.time.TimeSource.markNow")).single()

    private fun findElapsedNowFunIrReference(): IrSimpleFunctionSymbol =
        pluginContext.referenceFunctions(FqName("kotlin.time.TimeMark.elapsedNow")).single()

    private fun irDebug(
        function: IrFunction,
        body: IrBody,
    ): IrBlockBody = DeclarationIrBuilder(pluginContext, function.symbol).irBlockBody {
        +irCallPrintFunctionInformation(function)

        val startTime = irTemporary(irCall(markNowFunIrReference).also { call ->
            call.dispatchReceiver = irGetObject(monotonicClassIrReference)
        })

        val tryBlock = irBlock(resultType = function.returnType) {
            body.statements.forEach { irStatement ->
                +irStatement
            }
        }.transform(DebugLogReturnTransformer(function, startTime), null)

        val finallyBlock = irBlock {
            +irDebugExit(function, startTime)
        }

        +IrTryImpl(startOffset, endOffset, tryBlock.type).apply {
            tryResult = tryBlock
            finallyExpression = finallyBlock
        }
    }

    private fun IrBuilderWithScope.irCallPrintFunctionInformation(
        function: IrFunction,
    ): IrCall {
        val irStringConcatenation = irConcat().apply {
            addArgument(irString("⇢ ${function.name}("))
            function.valueParameters.forEachIndexed { index, irValueParameter ->
                if (index > 0) {
                    addArgument(irString(", "))
                }
                addArgument(irString("${irValueParameter.name}="))
                addArgument(irGet(irValueParameter))
            }
            addArgument(irString(")"))
        }

        return irCall(printlnFunIrReference).apply {
            putValueArgument(0, irStringConcatenation)
        }
    }

    private fun IrBuilderWithScope.irDebugExit(
        function: IrFunction,
        startTime: IrValueDeclaration,
        result: IrExpression? = null,
    ): IrCall {
        val concat = irConcat()
        concat.addArgument(irString("⇠ ${function.name} ["))
        concat.addArgument(irCall(elapsedNowFunIrReference).also { call ->
            call.dispatchReceiver = irGet(startTime)
        })
        if (result != null) {
            concat.addArgument(irString("] = "))
            concat.addArgument(result)
        } else {
            concat.addArgument(irString("]"))
        }

        return irCall(printlnFunIrReference).also { call ->
            call.putValueArgument(0, concat)
        }
    }

    inner class DebugLogReturnTransformer(
        private val function: IrFunction,
        private val startTime: IrValueDeclaration,
    ) : IrElementTransformerVoidWithContext() {
        override fun visitReturn(expression: IrReturn): IrExpression {
            // Lambda return??
            if (expression.returnTargetSymbol != function.symbol) {
                return super.visitReturn(expression)
            }

            return DeclarationIrBuilder(pluginContext, function.symbol).irBlock {
                if (expression.value.type == typeUnit) {
                    +irDebugExit(function, startTime)
                    +expression
                } else {
                    val result = irTemporary(expression.value)
                    +irDebugExit(function, startTime, irGet(result))
                    +expression.apply {
                        value = irGet(result)
                    }
                }
            }
        }
    }
}