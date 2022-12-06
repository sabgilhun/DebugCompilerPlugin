package com.example.debuglogcompileplugin

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.annotation.DebugLog

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.button).setOnClickListener {
            val result = annotationTest("hello", "nono!!")
            (it as TextView).text = result
        }
    }

    @DebugLog
    private fun annotationTest(p1: String, paramHavingDefaultValue: String = "world"): String {
        if (p1.isEmpty()) {
            return ""
        }

        return p1 + paramHavingDefaultValue
    }
}