package com.blackbriar.musicsupervisor

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.content.Intent

class SecondActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        val buttonD: Button = findViewById(R.id.buttonD)
        val buttonE: Button = findViewById(R.id.buttonE)

        // New functionality for Button D: Launch the book entry screen
        buttonD.setOnClickListener {
            val intent = Intent(this, EntryActivity::class.java)
            startActivity(intent)
        }

        buttonE.setOnClickListener {
            // Original Toast functionality (kept for context)
        }
    }
}