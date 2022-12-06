package com.example.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class PluginTest {

    @Test
    fun `IR plugin success`() {
        val file = File("src/test/kotlin/com/example/processor/Main.kt")
        println(file.path)
        val result = compile(sourceFiles = listOf(SourceFile.fromPath(file)))
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    fun compile(
        sourceFiles: List<SourceFile>,
        plugin: ComponentRegistrar = DebugLogComponentRegistrar(),
    ): KotlinCompilation.Result {
        return KotlinCompilation().apply {
            sources = sourceFiles
            useIR = true
            compilerPlugins = listOf(plugin)
            inheritClassPath = true

        }.compile()
    }

    fun compile(
        sourceFile: SourceFile,
        plugin: ComponentRegistrar = DebugLogComponentRegistrar(),
    ): KotlinCompilation.Result {
        return compile(listOf(sourceFile), plugin)
    }
}