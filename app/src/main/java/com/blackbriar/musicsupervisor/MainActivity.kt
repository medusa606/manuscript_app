package com.blackbriar.musicsupervisor

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val buttonA: Button = findViewById(R.id.buttonA)
        val buttonB: Button = findViewById(R.id.buttonB)
        val buttonC: Button = findViewById(R.id.buttonC)

        buttonA.setOnClickListener {
            val intent = Intent(this, SecondActivity::class.java)
            startActivity(intent)
        }

        buttonB.setOnClickListener {
            // TODO: Add action for button B
            val intent = Intent(this, EntryActivity::class.java)
            startActivity(intent)
        }

        buttonC.setOnClickListener {
            // TODO: Add action for button C
        }
    }
}

class MusicSupervisorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("Firebase", "Initializing Firebase services...")

        // 1. Initialize Firebase App (needed for all services)
        FirebaseApp.initializeApp(this)

        // 2. Configure Firestore Settings (optional, but good for local development and persistence)
        val db = Firebase.firestore
//        val settings = FirebaseFirestoreSettings.Builder()
//            .setPersistenceEnabled(true) // Enable offline data persistence
//            .build()
//        db.firestoreSettings = settings

        // 3. Ensure User Authentication (Crucial for Firestore security rules)
        val auth = Firebase.auth

        if (auth.currentUser == null) {
            // If no user is signed in, sign in anonymously.
            // This is mandatory because your Firestore security rules require a 'userId'.
            auth.signInAnonymously()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Sign in success, update UI with the signed-in user's information
                        val user = auth.currentUser
                        Log.i("Firebase", "Anonymous sign-in successful. User ID: ${user?.uid}")
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.e("Firebase", "Anonymous sign-in failed.", task.exception)
                    }
                }
        } else {
            Log.i("Firebase", "User already signed in. User ID: ${auth.currentUser?.uid}")
        }
    }
}