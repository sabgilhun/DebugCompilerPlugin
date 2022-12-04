package com.example.debuglogcompileplugin

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.annotation.DebugLog

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.button).setOnClickListener {
            val result = annotationTest("hello")
            (it as TextView).text = result
        }
    }

    @DebugLog
    private fun annotationTest(param: String, paramHavingDefaultValue: String = "world"): String {
        val start = System.currentTimeMillis()
        val end = System.currentTimeMillis()
        val elapsed = end - start
        return elapsed.toString()
    }
}