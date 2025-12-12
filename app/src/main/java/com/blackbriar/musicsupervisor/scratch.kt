package com.blackbriar.musicsupervisor

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.blackbriar.musicsupervisor.data.local.db.AppDatabase
import com.blackbriar.musicsupervisor.data.local.db.RoomImporter
import com.blackbriar.musicsupervisor.data.local.repository.SearchRepository
import com.blackbriar.musicsupervisor.data.local.entity.ItemEntity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import org.json.JSONObject

class EntryActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val dbFirestore = Firebase.firestore

    private lateinit var editTextAuthor: AutoCompleteTextView
    private lateinit var editTextBookTitle: AutoCompleteTextView
    private lateinit var buttonSave: MaterialButton
    private lateinit var buttonFetch: MaterialButton
    private lateinit var imageViewCover: ImageView
    private lateinit var textViewBlurb: TextView

    private var fetchedBlurb: String = ""
    private var fetchedCoverUrl: String = ""

    private lateinit var appDatabase: AppDatabase
    private lateinit var searchRepository: SearchRepository
    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.entry_activity)

        // Firebase Auth
        auth = Firebase.auth
        userId = auth.currentUser?.uid ?: UUID.randomUUID().toString()

        // Views
        editTextAuthor = findViewById(R.id.edit_text_author)
        editTextBookTitle = findViewById(R.id.edit_text_book_title)
        buttonSave = findViewById(R.id.button_ok)
        buttonFetch = findViewById(R.id.button_fetch_details)
        imageViewCover = findViewById(R.id.image_view_cover)
        textViewBlurb = findViewById(R.id.text_view_blurb)

        // Room DB + Repository
        appDatabase = AppDatabase.getInstance(applicationContext)
        searchRepository = SearchRepository(appDatabase.itemDao())

        // Ensure DB is populated from JSON (optional, one-time)
        lifecycleScope.launch {
            RoomImporter.importJsonToRoomWithFts(appDatabase, applicationContext)
        }

        // Setup autocomplete for both fields
        setupAutocomplete()

        // Fetch button
        buttonFetch.setOnClickListener { fetchBookDetails() }

        // Save button
        buttonSave.setOnClickListener { saveBookEntry() }

        buttonSave.isEnabled = false
    }

    private fun setupAutocomplete() {
        val authorAdapter = ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line)
        val titleAdapter = ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line)

        editTextAuthor.setAdapter(authorAdapter)
        editTextBookTitle.setAdapter(titleAdapter)

        // Autocomplete for Author
        editTextAuthor.addTextChangedListener {
            val query = it.toString()
            if (query.length >= 2) {
                lifecycleScope.launch {
                    val results = searchRepository.search(query)
                    val authors = results.map { it.author }.distinct()
                    withContext(Dispatchers.Main) {
                        authorAdapter.clear()
                        authorAdapter.addAll(authors)
                        authorAdapter.notifyDataSetChanged()
                    }
                }
            }
        }

        // Autocomplete for Title
        editTextBookTitle.addTextChangedListener {
            val query = it.toString()
            if (query.length >= 2) {
                lifecycleScope.launch {
                    val results = searchRepository.search(query)
                    val titles = results.map { it.title }.distinct()
                    withContext(Dispatchers.Main) {
                        titleAdapter.clear()
                        titleAdapter.addAll(titles)
                        titleAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun fetchBookDetails() {
        val author = editTextAuthor.text.toString().trim()
        val title = editTextBookTitle.text.toString().trim()

        if (author.isEmpty() || title.isEmpty()) {
            Toast.makeText(this, "Please enter both author and book title.", Toast.LENGTH_SHORT).show()
            return
        }

        textViewBlurb.text = "Fetching book details..."
        imageViewCover.visibility = View.GONE
        buttonSave.isEnabled = false
        buttonFetch.isEnabled = false

        lifecycleScope.launch {
            try {
                val details = withContext(Dispatchers.IO) {
                    performBookDataFetch(title, author)
                }

                fetchedBlurb = details.blurb
                fetchedCoverUrl = details.coverUrl

                // Display blurb & metadata
                textViewBlurb.text = buildString {
                    append(if (details.blurb.isNotEmpty()) details.blurb else "No blurb found.")

                    if (details.categories.isNotEmpty())
                        append("\n\n📚 Google Categories: ${details.categories.joinToString(", ")}")

                    if (details.subjects.isNotEmpty())
                        append("\n\n📖 Subjects: ${details.subjects.joinToString(", ")}")

                    if (details.genres.isNotEmpty())
                        append("\n\n🏷️ Genres: ${details.genres.joinToString(", ")}")
                }

                // Load cover image
                if (fetchedCoverUrl.isNotEmpty()) {
                    imageViewCover.visibility = View.VISIBLE
                    imageViewCover.load(fetchedCoverUrl) {
                        crossfade(true)
                        crossfade(500)
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

    private fun performBookDataFetch(title: String, author: String): BookDetails {
        // Your existing Google Books + Open Library logic unchanged
        // Returns BookDetails with blurb, coverUrl, categories, subjects, genres
        TODO("Use your previous implementation here")
    }

    private fun saveBookEntry() {
        val author = editTextAuthor.text.toString().trim()
        val title = editTextBookTitle.text.toString().trim()

        if (author.isEmpty() || title.isEmpty()) {
            Toast.makeText(this, "Please enter both author and book title.", Toast.LENGTH_SHORT).show()
            return
        }

        val bookData = hashMapOf(
            "author" to author,
            "title" to title,
            "blurb" to fetchedBlurb,
            "coverUrl" to fetchedCoverUrl,
            "timestamp" to System.currentTimeMillis()
        )

        val collectionPath = "artifacts/manuscript/users/$userId/books"

        dbFirestore.collection(collectionPath)
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
