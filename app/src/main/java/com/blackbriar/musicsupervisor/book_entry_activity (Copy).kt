package com.blackbriar.musicsupervisor

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import java.util.UUID
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import org.json.JSONObject
import coil.load
import com.blackbriar.musicsupervisor.R

//for rounding coil image corners
import com.google.android.material.shape.MaterialShapeDrawable
import coil.transform.RoundedCornersTransformation
import com.google.android.material.shape.MaterialShapeUtils

class EntryActivity : AppCompatActivity() {

    // Firestore and Auth instances
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // View references
    private lateinit var editTextAuthor: TextInputEditText
    private lateinit var editTextBookTitle: TextInputEditText
    private lateinit var buttonSave: MaterialButton
    private lateinit var buttonFetch: MaterialButton
    private lateinit var imageViewCover: ImageView
    private lateinit var textViewBlurb: TextView

    // Data holders for fetched data
    private var fetchedBlurb: String = ""
    private var fetchedCoverUrl: String = ""

    // Coroutine scope for network operations
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // We use this placeholder for the secure Firestore path structure
    private val canvasAppId = "manuscript"
    private var userId: String = ""

    private suspend fun analyzeEmotion(blurb: String): Map<String, Double> {
        val apiUrl = "https://emotion-api-bst1.onrender.com/analyze"
        val url = URL(apiUrl)

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        // Prepare the JSON payload
        val jsonInput = JSONObject()
        // TODO might need to sanitise string, remove /cr etc
        jsonInput.put("text", blurb)

        // Send the request
        connection.outputStream.use { os ->
            val input = jsonInput.toString().toByteArray(Charsets.UTF_8)
            os.write(input, 0, input.size)
        }

        // Get the response
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            val errorText = connection.errorStream?.bufferedReader()?.readText()
            throw Exception("Emotion API error $responseCode: $errorText")
        }

        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
        val jsonResponse = JSONObject(responseBody)

        val emotionsJson = jsonResponse.getJSONObject("emotions")
        val emotions = mutableMapOf<String, Double>()

        val keys = emotionsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            emotions[key] = emotionsJson.getDouble(key)
        }

        return emotions
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.entry_activity)

        // Initialize Firebase instances (using KTX)
        auth = Firebase.auth
        db = Firebase.firestore

        // Get current user ID (using UUID for anonymous/unauthenticated users)
        userId = auth.currentUser?.uid ?: UUID.randomUUID().toString()

        // Link views from XML (do this before using them)
        editTextAuthor = findViewById(R.id.edit_text_author)
        editTextBookTitle = findViewById(R.id.edit_text_book_title)
        buttonSave = findViewById(R.id.button_ok)
        buttonFetch = findViewById(R.id.button_fetch_details)
        imageViewCover = findViewById(R.id.image_view_cover)
        textViewBlurb = findViewById(R.id.text_view_blurb)

        // Set up click listeners
        buttonFetch.setOnClickListener {
            fetchBookDetails()
        }

        buttonSave.setOnClickListener {
            saveBookEntry()
        }

        // Initial state
        buttonSave.isEnabled = false
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel all coroutines when the activity is destroyed
        scope.cancel()
    }

    private fun fetchBookDetails() {
        val author = editTextAuthor.text.toString().trim()
        val title = editTextBookTitle.text.toString().trim()

        // Build display
        textViewBlurb.text = buildString {
            append(if (details.blurb.isNotEmpty()) details.blurb else "No blurb found.")

            if (details.categories.isNotEmpty())
                append("\n\n📚 Google Categories: ${details.categories.joinToString(", ")}")

            if (details.subjects.isNotEmpty())
                append("\n\n📖 Subjects: ${details.subjects.joinToString(", ")}")

            if (details.genres.isNotEmpty())
                append("\n\n🏷️ Genres: ${details.genres.joinToString(", ")}")
        }


        if (author.isEmpty() || title.isEmpty()) {
            Toast.makeText(this, "Please enter both author and book title.", Toast.LENGTH_SHORT).show()
            return
        }

        // Reset state and show loading
        textViewBlurb.text = "Fetching book details..."
        imageViewCover.visibility = View.GONE
        buttonSave.isEnabled = false
        buttonFetch.isEnabled = false


        // Start coroutine for network operation
        scope.launch {
            try {

//                val (subjects, genres) = withContext(Dispatchers.IO) { fetchOpenLibraryData(title, author) }
                val details = withContext(Dispatchers.IO) {
                    performBookDataFetch(title, author)
                }

                // Update UI on the main thread
                fetchedBlurb = details.blurb
                fetchedCoverUrl = details.coverUrl

//                textViewBlurb.text = if (fetchedBlurb.isNotEmpty()) fetchedBlurb else "No blurb found."
//                textViewBlurb.text = buildString {
//                    append(if (fetchedBlurb.isNotEmpty()) fetchedBlurb else "No blurb found.")
//                    if (details.categories.isNotEmpty()) {
//                        append("\n\n📚 Categories: ${details.categories.joinToString(", ")}")
//                    }
//                }
                textViewBlurb.text = buildString {
                    append(if (fetchedBlurb.isNotEmpty()) fetchedBlurb else "No blurb found.")

                    if (details.categories.isNotEmpty()) {
                        append("\n\n📚 Google Categories: ${details.categories.joinToString(", ")}")
                    }

                    if (subjects.isNotEmpty()) {
                        append("\n\n📖 OPEN Subjects: ${subjects.joinToString(", ")}")
                    }

                    if (genres.isNotEmpty()) {
                        append("\n\n🏷️ OPEN Genres: ${genres.joinToString(", ")}")
                    }
                }

                // Read emotions in blurb
                if (fetchedBlurb.isNotEmpty()) {
                    val emotions = withContext(Dispatchers.IO) { analyzeEmotion(fetchedBlurb) }
                    val formatted = emotions.entries.joinToString("\n") { (k, v) -> "$k: ${(v * 100).toInt()}%" }
                    textViewBlurb.append("\n\nEmotional profile:\n$formatted")
                }


                if (fetchedCoverUrl.isNotEmpty()) {
                    imageViewCover.visibility = View.VISIBLE

                    Log.d("BookCover", "Cover URL: $fetchedCoverUrl")


                    imageViewCover.load(fetchedCoverUrl) {
                        crossfade(true)
                        crossfade(500) // duration in ms
//                        transformations(RoundedCornersTransformation(cornerRadiusPx))
                        placeholder(R.drawable.ic_launcher_background)
                        error(R.drawable.ic_launcher_background)
                    }
                } else {
                    imageViewCover.visibility = View.GONE
                }

                buttonSave.isEnabled = true
                Toast.makeText(this@EntryActivity, "Details fetched successfully!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                textViewBlurb.text = "Error fetching details: ${e.message}"
                Log.e("BookSearch", "API call failed", e)
                Toast.makeText(this@EntryActivity, "Error fetching details.", Toast.LENGTH_LONG).show()
                buttonSave.isEnabled = false
            } finally {
                buttonFetch.isEnabled = true
            }
        }
    }

//     Open Library API call for genre and/or subject
//    private fun fetchOpenLibraryData(title: String, author: String): Pair<List<String>, List<String>> {
//        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
//        val encodedAuthor = java.net.URLEncoder.encode(author, "UTF-8")
//        val apiUrl = "https://openlibrary.org/search.json?title=$encodedTitle&author=$encodedAuthor&limit=1"
//
//        val url = URL(apiUrl)
//        val connection = url.openConnection() as HttpURLConnection
//        connection.requestMethod = "GET"
//        connection.connectTimeout = 5000
//
//        val responseCode = connection.responseCode
//        if (responseCode != HttpURLConnection.HTTP_OK) {
//            throw Exception("Open Library API Error $responseCode: ${connection.errorStream?.bufferedReader()?.readText()}")
//        }
//
//        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
//        val jsonResponse = JSONObject(responseBody)
//
//        var subjects: List<String> = emptyList()
//        var genres: List<String> = emptyList()
//
//        if (jsonResponse.has("docs") && jsonResponse.getJSONArray("docs").length() > 0) {
//            val doc = jsonResponse.getJSONArray("docs").getJSONObject(0)
//
//            if (doc.has("subject")) {
//                val jsonArray = doc.getJSONArray("subject")
//                subjects = (0 until jsonArray.length()).map { i -> jsonArray.getString(i) }
//            }
//
//            if (doc.has("genre")) {
//                val jsonArray = doc.getJSONArray("genre")
//                genres = (0 until jsonArray.length()).map { i -> jsonArray.getString(i) }
//            }
//        }
//
//        return Pair(subjects, genres)
//    }


    private fun performBookDataFetch(title: String, author: String): BookDetails {
        // 1️⃣ Encode title and author
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
        val encodedAuthor = java.net.URLEncoder.encode(author, "UTF-8")

        // 2️⃣ Google Books API
        val googleApiUrl =
            "https://www.googleapis.com/books/v1/volumes?q=intitle:$encodedTitle+inauthor:$encodedAuthor&maxResults=1&fields=items(volumeInfo(description,imageLinks,categories))"

        val googleUrl = URL(googleApiUrl)
        val googleConn = googleUrl.openConnection() as HttpURLConnection
        googleConn.requestMethod = "GET"
        googleConn.connectTimeout = 5000

        var blurb = ""
        var coverUrl = ""
        var categories: List<String> = emptyList()

        if (googleConn.responseCode == HttpURLConnection.HTTP_OK) {
            val responseBody = googleConn.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(responseBody)

            if (jsonResponse.has("items") && jsonResponse.getJSONArray("items").length() > 0) {
                val item = jsonResponse.getJSONArray("items").getJSONObject(0)
                val volumeInfo = item.getJSONObject("volumeInfo")

                blurb = volumeInfo.optString("description", "")

                if (volumeInfo.has("categories")) {
                    val jsonArray = volumeInfo.getJSONArray("categories")
                    categories = (0 until jsonArray.length()).map { i -> jsonArray.getString(i) }
                }

                if (volumeInfo.has("imageLinks")) {
                    val imageLinks = volumeInfo.getJSONObject("imageLinks")
                    coverUrl = imageLinks.optString("thumbnail", "")
                        .replace("http://", "https://")
                    if (coverUrl.isEmpty()) {
                        coverUrl = imageLinks.optString("smallThumbnail", "")
                            .replace("http://", "https://")
                    }
                }
            }
        }

        // 3️⃣ Open Library API
        val openLibApiUrl =
            "https://openlibrary.org/search.json?title=$encodedTitle&author=$encodedAuthor&limit=1"

        val openUrl = URL(openLibApiUrl)
        val openConn = openUrl.openConnection() as HttpURLConnection
        openConn.requestMethod = "GET"
        openConn.connectTimeout = 5000

        var subjects: List<String> = emptyList()
        var genres: List<String> = emptyList()

        if (openConn.responseCode == HttpURLConnection.HTTP_OK) {
            val responseBody = openConn.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(responseBody)

            if (jsonResponse.has("docs") && jsonResponse.getJSONArray("docs").length() > 0) {
                val doc = jsonResponse.getJSONArray("docs").getJSONObject(0)

                if (doc.has("subject")) {
                    val jsonArray = doc.getJSONArray("subject")
                    subjects = (0 until jsonArray.length()).map { i -> jsonArray.getString(i) }
                }

                if (doc.has("genre")) {
                    val jsonArray = doc.getJSONArray("genre")
                    genres = (0 until jsonArray.length()).map { i -> jsonArray.getString(i) }
                }
            }
        }

        return BookDetails(
            blurb = blurb,
            coverUrl = coverUrl,
            categories = categories,
            subjects = subjects,
            genres = genres
        )
    }



    private fun saveBookEntry() {
        val author = editTextAuthor.text.toString().trim()
        val title = editTextBookTitle.text.toString().trim()

        if (author.isEmpty() || title.isEmpty()) {
            Toast.makeText(this, "Please enter both author and book title.", Toast.LENGTH_SHORT).show()
            return
        }

        // Create the book data map
        val bookData = hashMapOf(
            "author" to author,
            "title" to title,
            "blurb" to fetchedBlurb,
            "coverUrl" to fetchedCoverUrl,
            "timestamp" to System.currentTimeMillis()
        )

        // Construct the Firestore collection path
        val collectionPath = "artifacts/$canvasAppId/users/$userId/books"

        db.collection(collectionPath)
            .add(bookData)
            .addOnSuccessListener { documentReference ->
                Log.d("Firestore", "DocumentSnapshot added with ID: ${documentReference.id}")
                Toast.makeText(this, "Book entry saved successfully!", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.w("Firestore", "Error adding document", e)
                Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}

data class BookDetails(
    val blurb: String,
    val coverUrl: String,
    val categories: List<String> = emptyList(),
    val subjects: List<String> = emptyList(),
    val genres: List<String> = emptyList()
)
