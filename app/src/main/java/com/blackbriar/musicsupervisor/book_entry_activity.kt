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
import coil3.Image
import coil3.toBitmap

import com.blackbriar.musicsupervisor.data.local.db.AppDatabase
import com.blackbriar.musicsupervisor.data.local.db.importJsonToRoomWithFts
import com.blackbriar.musicsupervisor.data.local.repository.SearchRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
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

// for image colour extraction and background
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import androidx.palette.graphics.Palette
import coil3.request.ImageResult
import coil3.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
import kotlinx.coroutines.withContext
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.Canvas
import android.content.res.ColorStateList
import coil3.request.bitmapConfig
import com.android.volley.toolbox.ImageRequest


private fun isColorLight(color: Int): Boolean {
    val darkness =
        1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
    return darkness < 0.5
}

fun loadCoverWithDynamicBackground(
    coverUrl: String,
    coverImageView: ImageView,
    backgroundView: View,
    textViews: List<TextView> = emptyList(),
    actionButtons: List<Button> = emptyList()

) {
    val fallbackBgColor = 0xFF121212.toInt() // Deep Charcoal/Black
    val initialGradient = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(fallbackBgColor, Color.BLACK)
    )
    backgroundView.background = initialGradient

    // Ensure text is white for dark backgrounds
    textViews.forEach { it.setTextColor(Color.WHITE) }

    if (coverUrl.isEmpty()) {
        coverImageView.isVisible = false
        return
    }

    coverImageView.isVisible = true

    coverImageView.load(coverUrl) {
        crossfade(true)

        bitmapConfig(Bitmap.Config.ARGB_8888)

        listener(
            onSuccess = { _, result ->
                // Safely convert the drawable to a bitmap
                val bitmap = (result.image as? BitmapDrawable)?.bitmap
                    ?: result.image.toBitmap()

                Palette.from(bitmap).generate { palette ->
                    // Default fallback color (Dark Gray/Black)
                    val defaultColor = fallbackBgColor

                    val accentColor: Int = palette?.getVibrantColor(
                        palette.getLightVibrantColor(Color.LTGRAY)
                    ) ?: Color.LTGRAY


                    val dominantColor = palette?.getDarkVibrantColor(
                        palette.getDarkVibrantColor(
                            palette.getDominantColor(defaultColor)
                        )
                    ) ?: defaultColor

                    val gradientDrawable = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(dominantColor, Color.BLACK)
                    )

                    backgroundView.animate().cancel()
                    backgroundView.setBackgroundColor(0xFF121212.toInt())
                    backgroundView.alpha = 0f
                    backgroundView.background = gradientDrawable
//                    if (backgroundView.alpha == 0f) {
//                        backgroundView.alpha = 0f
//                    }
                    backgroundView.animate()
                        .alpha(1f)
                        .setDuration(1400) // Slightly longer duration for a "cinematic" feel
                        .start()

                    textViews
                        .filterIsInstance<TextView>()
                        .forEach { it.setTextColor(Color.WHITE) }

                    actionButtons.forEach { button ->
                        button.backgroundTintList = ColorStateList.valueOf(accentColor)

                        val isLightAccent = isColorLight(accentColor)
                        button.setTextColor(if (isLightAccent) Color.BLACK else Color.WHITE)
                    }
                }
            }
        )
    }
}



class EntryActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val dbFirestore = Firebase.firestore

    private lateinit var editTextBookTitle: MaterialAutoCompleteTextView
    private lateinit var editTextAuthor: MaterialAutoCompleteTextView
    private lateinit var headerTitle: TextView

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
    private var selectedAuthor: String? = null
    private var isAuthorSelection = false
    private lateinit var backgroundView: View

    private var fetchedRating: Double = 0.0
    private var fetchedRatingsCount: Int = 0
    private var fetchedRatingSource: String = ""
    private lateinit var ratingBar: RatingBar
    private lateinit var textViewRating: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.entry_activity)
        backgroundView = findViewById(R.id.backgroundView)
        editTextAuthor = findViewById(R.id.edit_text_author)
        editTextBookTitle = findViewById(R.id.edit_text_book_title)
        buttonSave = findViewById(R.id.button_ok)
        buttonFetch = findViewById(R.id.button_fetch_details)
        imageViewCover = findViewById(R.id.image_view_cover)
        textViewBlurb = findViewById(R.id.text_view_blurb)
        headerTitle = findViewById(R.id.headerTitle) // Make sure this exists in your layout
        ratingBar = findViewById(R.id.rating_bar)
        textViewRating = findViewById(R.id.text_view_rating)


        backgroundView.setBackgroundColor(0xFF121212.toInt())
        textViewBlurb.setTextColor(Color.WHITE)
        listOf(
            headerTitle,
            editTextAuthor,
            editTextBookTitle
        ).forEach {
            if (it is TextView) it.setTextColor(Color.WHITE)
        }

        // Firebase Auth
        auth = Firebase.auth
        userId = auth.currentUser?.uid ?: UUID.randomUUID().toString()


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
//        buttonSave.setOnClickListener { saveBookEntry() }
        buttonSave.isEnabled = false

        ratingBar = findViewById(R.id.rating_bar)
        textViewRating = findViewById(R.id.text_view_rating)
    }

    private fun setupAutocomplete() {
        val authorAdapter = ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line)
        val titleAdapter = ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line)


        editTextAuthor.setAdapter(authorAdapter)

        // closes the author suggestions when user selects
        editTextAuthor.setOnItemClickListener { _, _, position, _ ->
            isAuthorSelection = true
            selectedAuthor = authorAdapter.getItem(position)
            editTextAuthor.dismissDropDown()
        }

        editTextBookTitle.setAdapter(titleAdapter)
        editTextAuthor.threshold = 1

        // --- Author autocomplete ---
        editTextAuthor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                // 🔒 Ignore changes caused by clicking a suggestion
                if (isAuthorSelection) {
                    isAuthorSelection = false
                    return
                }

                val query = s?.toString()?.trim().orEmpty()
                selectedAuthor = null

                if (query.length >= 1) {
                    lifecycleScope.launch {
                        val authors = searchRepository.searchAuthors(query)
                        withContext(Dispatchers.Main) {
                            authorAdapter.clear()
                            authorAdapter.addAll(authors.distinct())
                            authorAdapter.notifyDataSetChanged()
                        }
                    }
                } else {
                    authorAdapter.clear()
                    editTextAuthor.dismissDropDown()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // --- Title autocomplete ---
        editTextBookTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                val selectedAuthor = editTextAuthor.text.toString().trim() // Filter by author
                if (query.length >= 1 && selectedAuthor.isNotEmpty()) {
                    lifecycleScope.launch {
                        // Search titles filtered by author
                        val results = searchRepository.searchTitles(query, selectedAuthor)
                        val titles = results.distinct() // List<String>
                        withContext(Dispatchers.Main) {
                            titleAdapter.clear()
                            titleAdapter.addAll(titles)
                            titleAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    suspend fun performBookDataFetch(title: String, author: String): BookDetails {
        // take exact title author match
        val item = searchRepository.getExactItem(title, author)
            ?: return BookDetails(
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


       // Use this in your fetchBookDetails function:
    private fun fetchBookDetails() {
        val view = this.currentFocus
        if (view != null) {
            val imm =
                getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
        val author = editTextAuthor.text.toString().trim()
        val title = editTextBookTitle.text.toString().trim()

        if (author.isEmpty() || title.isEmpty()) {
            Toast.makeText(this, "Please enter both author and book title.", Toast.LENGTH_SHORT)
                .show()
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

                val googleBookData = fetchBookDataWithHighResCovers(title, author)
                fetchedCoverUrl = googleBookData.highResCoverUrl.ifEmpty { googleBookData.coverUrl }

                if (fetchedCoverUrl.isNotEmpty()) {
                    loadCoverWithDynamicBackground(
                        coverUrl = fetchedCoverUrl,
                        coverImageView = imageViewCover,
                        backgroundView = backgroundView,
                        textViews = listOf(textViewBlurb, textViewRating),
                        actionButtons = listOf(buttonSave, buttonFetch)
                    )
                } else {
                    imageViewCover.visibility = View.GONE
                }

                fetchedRating = googleBookData.averageRating
                fetchedRatingsCount = googleBookData.ratingsCount
                fetchedRatingSource = googleBookData.source

                fetchedBlurb = details.blurb
                fetchedThemes = details.themes
                fetchedMotif = details.motif
                fetchedTimePeriod = details.time_period
                fetchedLocation = details.location

                // Display blurb & metadata
                textViewBlurb.text = buildString {
                    append(if (details.blurb.isNotEmpty()) details.blurb else "No blurb found.")

                    // Add publish year if available
                    if (googleBookData.publishYear.isNotEmpty()) {
                        append("\n\n📅 Published: ${googleBookData.publishYear}")
                    }

                    if (details.categories.isNotEmpty())
                        append("\n\n📚 Categories: ${details.categories.joinToString(", ")}")

                    if (details.subjects.isNotEmpty())
                        append("\n\n📖 Subjects: ${details.subjects.joinToString(", ")}")

                    if (details.genres.isNotEmpty())
                        append("\n\n\uD83C\uDFAD Genres: ${details.genres.joinToString(", ")}")

                    if (details.themes.isNotEmpty())
                        append("\n\n\uD83E\uDDE9 Themes: ${details.themes.joinToString(", ")}")

                    if (details.motif.isNotEmpty())
                        append("\n\n\u269C\uFE0F Motifs: ${details.motif.joinToString(", ")}")

                    if (details.time_period.isNotEmpty())
                        append("\n\n\u23F3 Time Period: ${details.time_period.joinToString(", ")}")

                    if (details.location.isNotEmpty())
                        append("\n\n\uD83E\uDDED Location: ${details.location.joinToString(", ")}")

                    if (googleBookData.isbn.isNotEmpty()) {
                        append("\n\n\uD83C\uDD94 ISBN: ${googleBookData.isbn}")
                    }
                }

                // Display rating
                if (fetchedRating > 0) {
                    ratingBar.rating = fetchedRating.toFloat()
                    ratingBar.visibility = View.VISIBLE

                    val ratingText = String.format(
                        Locale.getDefault(),
                        "%.1f/5 (%s ratings via %s)",
                        fetchedRating,
                        formatRatingsCount(fetchedRatingsCount),
                        fetchedRatingSource
                    )
                    textViewRating.text = ratingText
                    textViewRating.visibility = View.VISIBLE
                } else {
                    ratingBar.visibility = View.GONE
                    textViewRating.text = "No ratings available"
                    textViewRating.visibility = View.VISIBLE
                }

                // Load high-quality cover image with Coil
                if (fetchedCoverUrl.isNotEmpty()) {
                    imageViewCover.visibility = View.VISIBLE
                    imageViewCover.load(fetchedCoverUrl) {
                        crossfade(true)
                        crossfade(500)
                        // Use high quality bitmap config for better rendering
                        bitmapConfig(Bitmap.Config.ARGB_8888)
                        // Add placeholder for better UX
//                        placeholder(R.drawable.book_placeholder)
//                        error(R.drawable.book_placeholder)
                    }
                } else {
                    imageViewCover.visibility = View.GONE
                }

                loadCoverWithDynamicBackground(
                    coverUrl = fetchedCoverUrl,
                    coverImageView = imageViewCover,
                    backgroundView = backgroundView,
                    textViews = listOf(textViewBlurb, textViewRating),
                    actionButtons = listOf(buttonSave, buttonFetch)
                )

                buttonSave.isEnabled = true
                Toast.makeText(
                    this@EntryActivity,
                    "Details fetched successfully!",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                textViewBlurb.text = "Error fetching details: ${e.message}"
                Log.e("BookSearch", "API call failed", e)
                Toast.makeText(this@EntryActivity, "Error fetching details.", Toast.LENGTH_LONG)
                    .show()
                buttonSave.isEnabled = false
            } finally {
                buttonFetch.isEnabled = true
            }
        }
    }



    private suspend fun fetchBookDataWithHighResCovers(title: String, author: String): GoogleBookData {
        return withContext(Dispatchers.IO) {
            var bestData = GoogleBookData()

            try {
                val query = "intitle:${title}+inauthor:${author}"
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                // Increase maxResults to 5 so we can find the best edition
//                val url = URL("https://www.googleapis.com/books/v1/volumes?q=$encodedQuery&maxResults=9&orderBy=relevance")
                val url = URL("https://www.googleapis.com/books/v1/volumes?q=$encodedQuery&orderBy=newest&printType=books&maxResults=5")
                val connection = url.openConnection() as HttpURLConnection
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                if (json.has("items")) {
                    val items = json.getJSONArray("items")
                    var item = items.getJSONObject(0)

                    // Optional: If the first 'newest' result has no image, check the second one
                    for (i in 0 until items.length()) {
                        val tempItem = items.getJSONObject(i)
                        if (tempItem.getJSONObject("volumeInfo").has("imageLinks")) {
                            item = tempItem
                            break
                        }
                    }
                    val volumeInfo = item.getJSONObject("volumeInfo")
                    val currentRating = volumeInfo.optDouble("averageRating", 0.0)
                    val imageLinks = volumeInfo.optJSONObject("imageLinks")
                    val thumb = imageLinks?.optString("thumbnail")?.replace("http://", "https://") ?: ""
                    val highResUrl = if (thumb.isNotEmpty()) "$thumb&fife=w800" else ""

                    // Extract ISBN-13
                    var isbn = ""
                    val identifiers = volumeInfo.optJSONArray("industryIdentifiers")
                    identifiers?.let {
                        for (j in 0 until it.length()) {
                            val id = it.getJSONObject(j)
                            if (id.getString("type").contains("ISBN_13")) {
                                isbn = id.getString("identifier")
                                break
                            }
                        }
                    }
                    bestData = GoogleBookData(
                        coverUrl = thumb,
                        highResCoverUrl = highResUrl,
                        averageRating = currentRating,
                        ratingsCount = volumeInfo.optInt("ratingsCount", 0),
                        publishYear = volumeInfo.optString("publishedDate", "").take(4),
                        source = "Google Books",
                        isbn = isbn
                    )


                    /// ****************************************

                    // LOOP through results to find the edition with the best metadata
                 /*   for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val volumeInfo = item.optJSONObject("volumeInfo") ?: continue

                        val currentRating = volumeInfo.optDouble("averageRating", 0.0)
                        val imageLinks = volumeInfo.optJSONObject("imageLinks")
                        val hasCover = imageLinks != null && imageLinks.has("thumbnail")

                        // Priority: If this edition has a rating AND a cover, it's our winner
                        if (currentRating > 1 && hasCover) {
                            val thumb = imageLinks?.optString("thumbnail")?.replace("http://", "https://") ?: ""
                            val highResUrl = if (thumb.isNotEmpty()) "$thumb&fife=w800" else ""

                            // Extract ISBN-13
                            var isbn = ""
                            val identifiers = volumeInfo.optJSONArray("industryIdentifiers")
                            identifiers?.let {
                                for (j in 0 until it.length()) {
                                    val id = it.getJSONObject(j)
                                    if (id.getString("type").contains("ISBN_13")) {
                                        isbn = id.getString("identifier")
                                        break
                                    }
                                }
                            }

                            // Update bestData if this one is better than what we have
                            // (i.e., it has a rating or we haven't found a cover yet)
                            if (currentRating > bestData.averageRating || (bestData.highResCoverUrl.isEmpty() && hasCover)) {
                                bestData = GoogleBookData(
                                    coverUrl = thumb,
                                    highResCoverUrl = highResUrl,
                                    averageRating = currentRating,
                                    ratingsCount = volumeInfo.optInt("ratingsCount", 0),
                                    publishYear = volumeInfo.optString("publishedDate", "").take(4),
                                    source = "Google Books",
                                    isbn = isbn
                                )
                            }
                        }

                        // If we found a "Perfect" result (Rating > 0 and Cover exists), stop looking
                        if (bestData.averageRating > 0 && bestData.highResCoverUrl.isNotEmpty()) break
                    }*/




                }
            } catch (e: Exception) {
                Log.e("BookSearch", "Google API fail: ${e.message}")
            }

            // Final Safety Check: If Google gave us no cover, use Open Library
            if (bestData.highResCoverUrl.isEmpty() && bestData.isbn.isNotEmpty()) {
                val olUrl = "https://covers.openlibrary.org/b/isbn/${bestData.isbn}-L.jpg?default=false"
                bestData = bestData.copy(highResCoverUrl = olUrl, source = "Open Library")
            }

            bestData
        }
    }

    /*
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
    */

    /*private fun fetchBookData(title: String, author: String): GoogleBookData {
        var bookData = GoogleBookData()

        // Google Books API - Primary source
        try {
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val encodedAuthor = java.net.URLEncoder.encode(author, "UTF-8")
            val googleApiUrl =
                "https://www.googleapis.com/books/v1/volumes?q=intitle:$encodedTitle+inauthor:$encodedAuthor&maxResults=1&fields=items(volumeInfo(imageLinks,averageRating,ratingsCount))"
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

                    // Extract cover URL
                    var coverUrl = ""
                    if (volumeInfo.has("imageLinks")) {
                        val imageLinks = volumeInfo.getJSONObject("imageLinks")
                        coverUrl =
                            imageLinks.optString("thumbnail", "").replace("http://", "https://")
                        if (coverUrl.isEmpty()) {
                            coverUrl = imageLinks.optString("smallThumbnail", "")
                                .replace("http://", "https://")
                        }
                    }

                    // Extract rating data
                    val averageRating = volumeInfo.optDouble("averageRating", 0.0)
                    val ratingsCount = volumeInfo.optInt("ratingsCount", 0)

                    bookData = GoogleBookData(
                        coverUrl = coverUrl,
                        averageRating = averageRating,
                        ratingsCount = ratingsCount,
                        source = "google"
                    )

                    // If we have cover and rating, return immediately
                    if (coverUrl.isNotEmpty() && averageRating > 0) {
                        return bookData
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BookDataFetch", "Failed to fetch from Google Books", e)
        }

        // Open Library fallback (for cover if Google didn't have one)
        if (bookData.coverUrl.isEmpty()) {
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

                    if (jsonResponse.has("docs") && jsonResponse.getJSONArray("docs")
                            .length() > 0
                    ) {
                        val doc = jsonResponse.getJSONArray("docs").getJSONObject(0)

                        // Cover
                        val coverId = doc.optInt("cover_i", -1)
                        if (coverId != -1) {
                            bookData = bookData.copy(
                                coverUrl = "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
                            )
                        }

                        // Rating data (Open Library has limited rating data)
                        val ratingsAverage = doc.optDouble("ratings_average", 0.0)
                        val ratingsCount = doc.optInt("ratings_count", 0)

                        if (ratingsAverage > 0 && bookData.averageRating == 0.0) {
                            bookData = bookData.copy(
                                averageRating = ratingsAverage,
                                ratingsCount = ratingsCount,
                                source = "openlibrary"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BookDataFetch", "Failed to fetch from Open Library", e)
            }
        }

        return bookData
    }*/

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

    /*private fun saveBookEntry() {
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
            "rating" to fetchedRating,
            "ratingsCount" to fetchedRatingsCount,
            "ratingSource" to fetchedRatingSource,
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
    }*/


    // Helper function to format large rating counts
    private fun formatRatingsCount(count: Int): String {
        return when {
            count >= 1000 -> "${count / 1000}k+"
            else -> count.toString()
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


    // Data class to hold book information
    data class GoogleBookData(
        val coverUrl: String = "",
        val highResCoverUrl: String = "", // For highest quality available
        val averageRating: Double = 0.0,
        val ratingsCount: Int = 0,
        val source: String = "", // "google", "openlibrary", "goodreads"
        val publishYear: String = "", // To help choose most recent edition
        val isbn: String = "" // To fetch from multiple sources
    )
