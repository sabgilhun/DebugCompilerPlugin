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
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.functions
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

    private val logClassIrReference by lazy {
        checkNotNull(pluginContext.referenceClass(FqName("android.util.Log")))
    }

    private val systemClassIrReference by lazy {
        checkNotNull(pluginContext.referenceClass(FqName("java.lang.System")))
    }

    private val debugLogAnnotationIrReference by lazy {
        findAnnotationIrReference(pluginContext)
    }

    private val logFunIrReference by lazy {
        findLogFunIrReference()
    }

    private val currentTimeMillisFunIrReference by lazy {
        findCurrentTimeMillisFunIrReference()
    }

    private val longMinusFunIrReference by lazy {
        findLongMinusFunIrReference(pluginContext)
    }

    /** nav-15
     * ?????? ?????? node ??????????????? 'visitFunctionNew' ?????????
     * ?????? ????????? ????????? ??????????????? 'visitFunctionNew' override
     **/
    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        val body = declaration.body

        /** nav-16
         * @DebugLog symbol??? annotation?????? ?????? ?????? ?????? ??????
         **/
        if (body != null && declaration.hasAnnotation(debugLogAnnotationIrReference)) {
            /** nav-18
             * ?????? body??? ????????? body??? ??????
             **/
            val generatedBody = irDebug(declaration, body)
            declaration.body = generatedBody

            /** nav-28
             *  ??????????????? ???????????? body ??????
             *  dump()??? ?????? IR tree Text ????????? ????????????
             *  messageCollector ?????? log ?????? ??? ??????, gradle logging ?????? ????????? ?????? ??? ??? ??????
             **/
            messageCollector.report(CompilerMessageSeverity.INFO, "Generated Function Body")
            messageCollector.report(CompilerMessageSeverity.INFO, generatedBody.dump())
        }
        return super.visitFunctionNew(declaration)
    }

    /** nav-17
     * symbol??? ????????? pluginContext?????? ??????
     **/
    private fun findAnnotationIrReference(
        pluginContext: IrPluginContext,
    ): IrClassSymbol =
        checkNotNull(pluginContext.referenceClass(FqName("com.example.annotation.DebugLog")))

    private fun findLogFunIrReference(): IrSimpleFunctionSymbol {
        val loggingFunName = when (logLevel) {
            "verbose" -> "v"
            "info" -> "i"
            "debug" -> "d"
            "warning" -> "w"
            "error" -> "e"
            else -> throw IllegalStateException("logLevel ?????? ?????? ???????????? ????????????.")
        }

        /** nav-21
         * Java Code ??? ?????? ?????? ?????? ?????? ??????
         **/
        return logClassIrReference.functions.single {
            val parameters = it.owner.valueParameters
            it.owner.name.asString() == loggingFunName && parameters.size == 2
        }
    }

    private fun findCurrentTimeMillisFunIrReference(): IrSimpleFunctionSymbol =
        systemClassIrReference.functions.single {
            val parameters = it.owner.valueParameters
            it.owner.name.asString() == "currentTimeMillis" && parameters.isEmpty()
        }

    private fun findLongMinusFunIrReference(
        pluginContext: IrPluginContext,
    ): IrSimpleFunctionSymbol =
        pluginContext.referenceFunctions(FqName("kotlin.Long.minus")).single {
            val typeLong = pluginContext.irBuiltIns.longType
            val parameters = it.owner.valueParameters
            parameters.size == 1 && parameters[0].type == typeLong
        }

    /** nav-19
     * ????????? ?????? body??? ??????
     **/
    private fun irDebug(
        function: IrFunction,
        body: IrBody,
    ): IrBlockBody = DeclarationIrBuilder(pluginContext, function.symbol).irBlockBody {
        /** nav-20
         * Log.i(tag, "function name: origin") ????????? ??????
         **/
        +irCallLogFunctionName(function)

        /** nav-22
         * Log.i(tag, "params: [param1 = ${param1}, param2 = ${param2}]")
         **/
        +irCallLogFunctionParams(function)

        /** nav-23
         * val start = System.currentTimeMillis()
         **/
        val startVariableIr = irTemporary(irCall(currentTimeMillisFunIrReference))

        /** nav-23
         * try {
         *      < origin function body >
         *      val result = <original return>
         *      Log.i(tag, "result: ...")
         *      return ...
         *  }
         *  < origin function body > ?????? return??? ?????? ????????? ?????? ????????? DebugLogReturnTransformer??? ?????? ??????
         **/
        val tryBlock = irBlock(resultType = function.returnType) {
            body.statements.forEach { irStatement ->
                +irStatement
            }
        }.transform(DebugLogReturnTransformer(function), null)

        /** nav-26
         *  catch (t: Throwable) {
         *      Log.i(tag, "throw error: ${t}???)
         *      throw t
         *  }
         **/
        val throwable = buildVariable(
            scope.getLocalDeclarationParent(),
            startOffset,
            endOffset,
            IrDeclarationOrigin.CATCH_PARAMETER,
            Name.identifier("t"),
            pluginContext.irBuiltIns.throwableType
        )
        val catchBlock = irCatch(throwable, irBlock {
            +irCallLogThrowInformation(throwable)
            +irThrow(irGet(throwable))
        })

        /** nav-27
         *  finally {
         *      val end = System.currentTimeMillis()
         *      val elapsed = end - start
         *      Log.i(tag, "time elapsed: ${elapsed}ms")
         *   }
         **/
        val finallyBlock = irBlock {
            val endVariableIr = irTemporary(irCall(currentTimeMillisFunIrReference))
            val elapsed = irTemporary(irCall(longMinusFunIrReference).apply {
                dispatchReceiver = irGet(endVariableIr)
                putValueArgument(0, irGet(startVariableIr))
            })
            +irCallLogTimeElapsedInformation(elapsed)
        }

        +irTry(
            type = tryBlock.type,
            tryResult = tryBlock,
            catches = listOf(catchBlock),
            finallyExpression = finallyBlock
        )
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
            /** nav-24
             *  ?????? function??? return?????? ??????
             **/
            if (expression.returnTargetSymbol != function.symbol) {
                return super.visitReturn(expression)
            }

            /** nav-25
             *  val result = <original return>
             *  Log.i(tag, "result: ${result}")
             *  return ...
             **/
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
            return irCall(logFunIrReference).apply {
                putValueArgument(0, irString(logTag))
                putValueArgument(1, irStringConcatenation)
            }
        }
    }
}