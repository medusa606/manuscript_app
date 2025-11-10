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
                val details = withContext(Dispatchers.IO) {
                    performGoogleBooksApiCall(title, author)
                }

                // Update UI on the main thread
                fetchedBlurb = details.blurb
                fetchedCoverUrl = details.coverUrl

                textViewBlurb.text = if (fetchedBlurb.isNotEmpty()) fetchedBlurb else "No blurb found."

                if (fetchedCoverUrl.isNotEmpty()) {
                    imageViewCover.visibility = View.VISIBLE

                    Log.d("BookCover", "Cover URL: $fetchedCoverUrl")

                    // Use Coil to load the image from the URL
//                    imageViewCover.load(fetchedCoverUrl) {
//                        crossfade(true)
//                        error(R.drawable.ic_launcher_background)
//                    }
//                    Can choose rounding type for image
//                    m3_sys_shape_corner_small
//                    m3_sys_shape_corner_medium
//                    m3_sys_shape_corner_large
//                    val cornerRadiusPx = resources.getDimension(
//                        com.google.android.material.R.dimen.m3_sys_shape_corner_large)

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

    private fun performGoogleBooksApiCall(title: String, author: String): BookDetails {
        // Encode query parameters for URL safety
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
        val encodedAuthor = java.net.URLEncoder.encode(author, "UTF-8")

        // Query Google Books API, limiting to 1 result and only requesting the description and image links
        val apiUrl = "https://www.googleapis.com/books/v1/volumes?q=intitle:$encodedTitle+inauthor:$encodedAuthor&maxResults=1&fields=items(volumeInfo(description,imageLinks))"

        val url = URL(apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("API Error $responseCode: ${connection.errorStream?.bufferedReader()?.use { it.readText() }}")
        }

        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
        val jsonResponse = JSONObject(responseBody)

        var blurb = ""
        var coverUrl = ""

        if (jsonResponse.has("items") && jsonResponse.getJSONArray("items").length() > 0) {
            val item = jsonResponse.getJSONArray("items").getJSONObject(0)
            val volumeInfo = item.getJSONObject("volumeInfo")

            blurb = volumeInfo.optString("description", "")

//            if (volumeInfo.has("imageLinks")) {
//                val imageLinks = volumeInfo.getJSONObject("imageLinks")
//                coverUrl = imageLinks.optString("thumbnail", "")
//                if (coverUrl.isEmpty()) {
//                    coverUrl = imageLinks.optString("smallThumbnail", "")
//                }
//            }
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

        return BookDetails(blurb, coverUrl)
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
    val coverUrl: String
)
