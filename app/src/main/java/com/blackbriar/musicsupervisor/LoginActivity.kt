package com.blackbriar.musicsupervisor
//
//import android.content.Context
//import android.content.Intent
//import android.net.Uri
//import android.os.Bundle
//import android.widget.Button
//import androidx.appcompat.app.AppCompatActivity
//
//// this need to go somewhere
//val localProps = java.util.Properties()
//localProps.load(rootProject.file("local.properties").inputStream())
//
//android {
//    defaultConfig {
//        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${localProps["spotifyClientId"]}\"")
//    }
//}
//
//// to use this
//private const val CLIENT_ID = BuildConfig.SPOTIFY_CLIENT_ID
//
//
//class LoginActivity : AppCompatActivity() {
//
//    companion object {
//        private const val CLIENT_ID = SPOTIFY_CLIENT_ID_KEY
//        private const val REDIRECT_URI = "com.blackbriar.musicsupervisor://callback"
//    }
//
//    private lateinit var codeVerifier: String
//    private lateinit var codeChallenge: String
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_login)
//
//        val spotifyButton = findViewById<Button>(R.id.btnLoginSpotify)
//
//        spotifyButton.setOnClickListener {
//            startSpotifyLogin(this)
//        }
//    }
//
//    private fun startSpotifyLogin(context: Context) {
//        codeVerifier = PkceUtils.generateCodeVerifier()
//        codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier)
//
//        val authUri = Uri.Builder()
//            .scheme("https")
//            .authority("accounts.spotify.com")
//            .path("authorize")
//            .appendQueryParameter("client_id", CLIENT_ID)
//            .appendQueryParameter("response_type", "code")
//            .appendQueryParameter("redirect_uri", REDIRECT_URI)
//            .appendQueryParameter("scope", "playlist-modify-public playlist-modify-private user-read-email")
//            .appendQueryParameter("code_challenge_method", "S256")
//            .appendQueryParameter("code_challenge", codeChallenge)
//            .build()
//
//        val intent = Intent(Intent.ACTION_VIEW, authUri)
//        context.startActivity(intent)
//    }
//
//    override fun onResume() {
//        super.onResume()
//
//        val uri = intent?.data ?: return
//
//        if (uri.toString().startsWith(REDIRECT_URI)) {
//            val code = uri.getQueryParameter("code")
//            if (code != null) {
//                // SUCCESS — you now have the authorization code
//                // You will exchange this for an access token next step
//                handleAuthorizationCode(code)
//            } else {
//                val error = uri.getQueryParameter("error")
//                // Handle error if needed
//            }
//        }
//    }
//
//    private fun handleAuthorizationCode(code: String) {
//        // TODO: Next step will exchange code for access token
//        // For now just log or toast
//        println("Authorization Code: $code")
//    }
//}
