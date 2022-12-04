package com.example.processor

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irCatch
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildVariable
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrTryImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class DebugLogTransformer(
    private val messageCollector: MessageCollector,
    private val pluginContext: IrPluginContext,
    private val logTag: String,
    private val logLevel: String,
) : IrElementTransformerVoidWithContext() {

    private val debugLogAnnotationIrReference by lazy {
        findAnnotationIrReference(pluginContext)
    }

    private val logFunIrReference by lazy {
        findLogFunIrReference(pluginContext)
    }

    private val currentTimeMillisFunIrReference by lazy {
        findCurrentTimeMillisFunIrReference()
    }

    private val longMinusFunIrReference by lazy {
        findLongMinusFunIrReference(pluginContext)
    }

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        val body = declaration.body
        if (body != null && declaration.hasAnnotation(debugLogAnnotationIrReference)) {
            messageCollector.report(CompilerMessageSeverity.INFO, "here!!")
            messageCollector.report(CompilerMessageSeverity.INFO, body.dump())
            declaration.body = irDebug(declaration, body)
        }
        return super.visitFunctionNew(declaration)
    }

    private fun findAnnotationIrReference(
        pluginContext: IrPluginContext,
    ): IrClassSymbol =
        checkNotNull(pluginContext.referenceClass(FqName("com.example.annotation.DebugLog")))

    private fun findLogFunIrReference(
        pluginContext: IrPluginContext,
    ): IrSimpleFunctionSymbol {
        val loggingFunName = when (logLevel) {
            "verbose" -> "v"
            "info" -> "i"
            "debug" -> "d"
            "warning" -> "w"
            "error" -> "e"
            else -> throw IllegalStateException("logLevel 옵션 값이 올바르지 않습니다.")
        }
        val typeString = pluginContext.irBuiltIns.stringType
        return pluginContext.referenceFunctions(FqName("android.util.Log.$loggingFunName"))
            .single {
                val parameters = it.owner.valueParameters
                true
//                parameters.size == 2
//                        && parameters[0].type == typeString
//                        && parameters[0].type.isNullable()
//                        && parameters[1].type == typeString
//                        && !parameters[0].type.isNullable()
            }
    }

    private fun findCurrentTimeMillisFunIrReference(): IrSimpleFunctionSymbol =
        pluginContext.referenceFunctions(FqName("java.lang.System.currentTimeMillis")).single()


    private fun findLongMinusFunIrReference(
        pluginContext: IrPluginContext,
    ): IrSimpleFunctionSymbol =
        pluginContext.referenceFunctions(FqName("kotlin.Long.minus")).single {
            val typeLong = pluginContext.irBuiltIns.longType
            val parameters = it.owner.valueParameters
            parameters.size == 1 && parameters[0].type == typeLong
        }

    private fun irDebug(
        function: IrFunction,
        body: IrBody,
    ): IrBlockBody = DeclarationIrBuilder(pluginContext, function.symbol).irBlockBody {
        +irCallLogFunctionName(function)
        +irCallLogFunctionParams(function)

        val startVariableIr = irTemporary(irCall(currentTimeMillisFunIrReference))

        val tryBlock = irBlock(resultType = function.returnType) {
            body.statements.forEach { irStatement ->
                +irStatement
            }
        }.transform(DebugLogReturnTransformer(function), null)

        val throwable = buildVariable(
            scope.getLocalDeclarationParent(),
            startOffset,
            endOffset,
            IrDeclarationOrigin.CATCH_PARAMETER,
            Name.identifier("t"), pluginContext.irBuiltIns.throwableType
        )
        val catchBlock = irCatch(throwable, irBlock {
            +irCallLogThrowInformation(throwable)
            +irThrow(irGet(throwable))
        })

        val finallyBlock = irBlock {
            val elapsed = irTemporary(irCall(longMinusFunIrReference).apply {
                dispatchReceiver = irCall(currentTimeMillisFunIrReference)
                extensionReceiver = irGet(startVariableIr)
            })
            +irCallLogTimeElapsedInformation(elapsed)
        }

        +IrTryImpl(startOffset, endOffset, tryBlock.type).apply {
            tryResult = tryBlock
            catches += catchBlock
            finallyExpression = finallyBlock
        }
    }

    private fun IrBuilderWithScope.irCallLogFunctionName(
        function: IrFunction,
    ): IrCall = irCall(logFunIrReference).apply {
        putValueArgument(0, irString(logTag))
        putValueArgument(1, irString("function name: ${function.name}"))
    }

    private fun IrBuilderWithScope.irCallLogFunctionParams(
        function: IrFunction,
    ): IrCall {
        val irStringConcatenation = irConcat().apply {
            addArgument(irString("params: ["))
            function.valueParameters.forEachIndexed { index, irValueParameter ->
                addArgument(irString("${irValueParameter.name} = "))
                addArgument(irGet(irValueParameter))
                if (index != function.valueParameters.size - 1) {
                    addArgument(irString(", "))
                }
            }
            addArgument(irString("]"))
        }

        return irCall(logFunIrReference).apply {
            putValueArgument(0, irString(logTag))
            putValueArgument(1, irStringConcatenation)
        }
    }

    private fun IrBuilderWithScope.irCallLogThrowInformation(
        throwable: IrVariable,
    ): IrCall {
        val irStringConcatenation = irConcat().apply {
            addArgument(irString("throw error: "))
            addArgument(irGet(throwable))
        }
        return irCall(logFunIrReference).apply {
            putValueArgument(0, irString(logTag))
            putValueArgument(1, irStringConcatenation)
        }
    }

    private fun IrBuilderWithScope.irCallLogTimeElapsedInformation(
        elapsed: IrVariable,
    ): IrCall {
        val irStringConcatenation = irConcat().apply {
            addArgument(irString("time elapsed: "))
            addArgument(irGet(elapsed))
            addArgument(irString("ms"))
        }
        return irCall(logFunIrReference).apply {
            putValueArgument(0, irString(logTag))
            putValueArgument(1, irStringConcatenation)
        }
    }

    inner class DebugLogReturnTransformer(
        private val function: IrFunction,
    ) : IrElementTransformerVoidWithContext() {

        override fun visitReturn(expression: IrReturn): IrExpression {
            // why?
            if (expression.returnTargetSymbol != function.symbol) {
                return super.visitReturn(expression)
            }

            return DeclarationIrBuilder(pluginContext, function.symbol).irBlock {
                val resultVariableIr = irTemporary(expression.value)
                +irCallLogFunctionResult(resultVariableIr)
                +expression.apply {
                    value = irGet(resultVariableIr)
                }
            }
        }

        private fun IrBuilderWithScope.irCallLogFunctionResult(
            result: IrVariable,
        ): IrCall {
            val irStringConcatenation = irConcat().apply {
                addArgument(irString("result: "))
                addArgument(irGet(result))
            }
            return irCall(logFunIrReference).also { call ->
                call.putValueArgument(0, irString(logTag))
                call.putValueArgument(1, irStringConcatenation)
            }
        }
    }
}