package com.example.debuglogcompileplugin

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.annotation.DebugLog

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.button).setOnClickListener {
            annotationTest("hello")
        }
    }

    @DebugLog
    private fun annotationTest(param: String, paramHavingDefaultValue: String = "world") {
        println("$param $paramHavingDefaultValue")
    }
}