package com.blackbriar.musicsupervisor

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil3.load
import coil3.request.crossfade
import coil3.request.placeholder
import com.blackbriar.musicsupervisor.data.local.db.AppDatabase
import com.blackbriar.musicsupervisor.data.local.db.importJsonToRoomWithFts
import com.blackbriar.musicsupervisor.data.local.repository.SearchRepository
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import org.json.JSONObject
import android.text.TextWatcher
import android.text.Editable

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
    private var fetchedThemes: List<String> = emptyList()
    private var fetchedMotif: List<String> = emptyList()
    private var fetchedTimePeriod: List<String> = emptyList()
    private var fetchedLocation: List<String> = emptyList()

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
            importJsonToRoomWithFts(appDatabase, applicationContext)
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

        editTextAuthor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
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

            override fun afterTextChanged(s: Editable?) {}
        })
        editTextBookTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
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

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.cancel()
    }

    suspend fun performBookDataFetch(title: String, author: String): BookDetails {
        // Use the DAO to do a prefix search (or exact search if you prefer)
        val items = searchRepository.search("$title $author") // or searchPrefix(title) etc.

        if (items.isEmpty()) {
            // Return empty BookDetails if nothing found
            return BookDetails(
                blurb = "",
                coverUrl = "",
                categories = emptyList(),
                subjects = emptyList(),
                genres = emptyList(),
                themes = emptyList(),
                motif = emptyList(),
                time_period = emptyList(),
                location = emptyList()
            )
        }

        // Take the first matching item
        val item = items.first()

        // Helper function to split comma-separated strings into list
        fun splitToList(str: String): List<String> {
            return str.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

        return BookDetails(
            blurb = item.blurb,
            coverUrl = "", // We'll fetch cover separately
            categories = splitToList(item.category),
            subjects = splitToList(item.tags),
            genres = splitToList(item.genres),
            themes = splitToList(item.themes),
            motif = splitToList(item.motif),
            time_period = splitToList(item.time_period),
            location = splitToList(item.location)
        )
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
                val details: BookDetails = withContext(Dispatchers.IO) {
                    performBookDataFetch(title, author)
                }

                fetchedBlurb = details.blurb
                fetchedCoverUrl = withContext(Dispatchers.IO) { fetchCoverUrl(title, author) }
                fetchedThemes = details.themes
                fetchedMotif = details.motif
                fetchedTimePeriod = details.time_period
                fetchedLocation = details.location


                // Display blurb & metadata
                textViewBlurb.text = buildString {
                    append(if (details.blurb.isNotEmpty()) details.blurb else "No blurb found.")

                    if (details.categories.isNotEmpty())
                        append("\n\n📚 Categories: ${details.categories.joinToString(", ")}")

                    if (details.subjects.isNotEmpty())
                        append("\n\n📖 Subjects: ${details.subjects.joinToString(", ")}")

                    if (details.genres.isNotEmpty())
                        append("\n\n🏷️ Genres: ${details.genres.joinToString(", ")}")

                    if (details.themes.isNotEmpty())
                        append("\n\n🏷️ Themes: ${details.themes.joinToString(", ")}")

                    if (details.motif.isNotEmpty())
                        append("\n\n🏷️ Motifs: ${details.motif.joinToString(", ")}")

                    if (details.time_period.isNotEmpty())
                        append("\n\n🏷️ Time Period: ${details.time_period.joinToString(", ")}")

                    if (details.location.isNotEmpty())
                        append("\n\n🏷️ Location: ${details.location.joinToString(", ")}")
                }

                // Load cover image
                if (fetchedCoverUrl.isNotEmpty()) {
                    imageViewCover.visibility = View.VISIBLE
                    imageViewCover.load(fetchedCoverUrl) {
                        crossfade(true)
                        crossfade(500)
                        placeholder(R.drawable.ic_launcher_background) // resource ID is correct
                        error(R.drawable.ic_launcher_background)
                    }
//                    imageViewCover.load(fetchedCoverUrl) {
//                        crossfade(true)
//                        crossfade(500)
//                        placeholder(R.drawable.ic_launcher_background)
//                        error(R.drawable.ic_launcher_background)
//                    }
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

    private fun fetchCoverUrl(title: String, author: String): String {
        var coverUrl = ""

        // Google Books API
        try {
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val encodedAuthor = java.net.URLEncoder.encode(author, "UTF-8")
            val googleApiUrl =
                "https://www.googleapis.com/books/v1/volumes?q=intitle:$encodedTitle+inauthor:$encodedAuthor&maxResults=1&fields=items(volumeInfo(imageLinks))"
            val googleUrl = URL(googleApiUrl)
            val googleConn = googleUrl.openConnection() as HttpURLConnection
            googleConn.requestMethod = "GET"
            googleConn.connectTimeout = 5000

            if (googleConn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = googleConn.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(responseBody)

                if (jsonResponse.has("items") && jsonResponse.getJSONArray("items").length() > 0) {
                    val item = jsonResponse.getJSONArray("items").getJSONObject(0)
                    val volumeInfo = item.getJSONObject("volumeInfo")

                    if (volumeInfo.has("imageLinks")) {
                        val imageLinks = volumeInfo.getJSONObject("imageLinks")
                        coverUrl = imageLinks.optString("thumbnail", "").replace("http://", "https://")
                        if (coverUrl.isEmpty()) {
                            coverUrl = imageLinks.optString("smallThumbnail", "").replace("http://", "https://")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BookCoverFetch", "Failed to fetch cover", e)
        }

        // Open Library fallback
        try {
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val encodedAuthor = java.net.URLEncoder.encode(author, "UTF-8")
            val openLibApiUrl =
                "https://openlibrary.org/search.json?title=$encodedTitle&author=$encodedAuthor&limit=1"
            val openUrl = URL(openLibApiUrl)
            val openConn = openUrl.openConnection() as HttpURLConnection
            openConn.requestMethod = "GET"
            openConn.connectTimeout = 5000

            if (openConn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = openConn.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(responseBody)

                if (jsonResponse.has("docs") && jsonResponse.getJSONArray("docs").length() > 0) {
                    val doc = jsonResponse.getJSONArray("docs").getJSONObject(0)
                    val coverId = doc.optInt("cover_i", -1)
                    if (coverId != -1) {
                        coverUrl = "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BookCoverFetch", "Failed to fetch cover from Open Library", e)
        }

        return coverUrl
    }


    private suspend fun analyzeEmotion(blurb: String): Map<String, Double> {
        val apiUrl = "https://emotion-api-bst1.onrender.com/analyze"
        val url = URL(apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val jsonInput = JSONObject()
        jsonInput.put("text", blurb)

        connection.outputStream.use { os ->
            val input = jsonInput.toString().toByteArray(Charsets.UTF_8)
            os.write(input, 0, input.size)
        }

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

data class BookDetails(
    val blurb: String,
    val coverUrl: String,
    val categories: List<String> = emptyList(),
    val subjects: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val themes: List<String> = emptyList(),
    val motif: List<String> = emptyList(),
    val time_period: List<String> = emptyList(),
    val location: List<String> = emptyList()
)
