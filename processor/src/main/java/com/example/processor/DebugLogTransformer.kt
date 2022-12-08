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
     * 함수 선언 node 들릴때마다 'visitFunctionNew' 호출됨
     * 변경 하려는 부분이 함수이므로 'visitFunctionNew' override
     **/
    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        val body = declaration.body

        /** nav-16
         * @DebugLog symbol을 annotation으로 갖고 있는 함수 확인
         **/
        if (body != null && declaration.hasAnnotation(debugLogAnnotationIrReference)) {
            /** nav-18
             * 기존 body를 생성한 body로 변경
             **/
            val generatedBody = irDebug(declaration, body)
            declaration.body = generatedBody

            /** nav-28
             *  최종적으로 만들어진 body 출력
             *  dump()를 쓰면 IR tree Text 형태로 만들어줌
             *  messageCollector 통해 log 찍을 수 있고, gradle logging 옵션 켜두면 로그 볼 수 있음
             **/
            messageCollector.report(CompilerMessageSeverity.INFO, "Generated Function Body")
            messageCollector.report(CompilerMessageSeverity.INFO, generatedBody.dump())
        }
        return super.visitFunctionNew(declaration)
    }

    /** nav-17
     * symbol을 찾을땐 pluginContext에서 찾음
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
            else -> throw IllegalStateException("logLevel 옵션 값이 올바르지 않습니다.")
        }

        /** nav-21
         * Java Code 는 함수 찾는 법이 조금 다름
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
     * 새로운 함수 body를 생성
     **/
    private fun irDebug(
        function: IrFunction,
        body: IrBody,
    ): IrBlockBody = DeclarationIrBuilder(pluginContext, function.symbol).irBlockBody {
        /** nav-20
         * Log.i(tag, "function name: origin") 만드는 부분
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
         *  < origin function body > 내부 return을 모두 바꿔야 하기 때문에 DebugLogReturnTransformer로 다시 순회
         **/
        val tryBlock = irBlock(resultType = function.returnType) {
            body.statements.forEach { irStatement ->
                +irStatement
            }
        }.transform(DebugLogReturnTransformer(function), null)

        /** nav-26
         *  catch (t: Throwable) {
         *      Log.i(tag, "throw error: ${t}”)
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
             *  원래 function의 return인지 확인
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