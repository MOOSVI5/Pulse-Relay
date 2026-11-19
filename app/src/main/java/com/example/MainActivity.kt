package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.PulseRepository
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.authStateFlow
import com.example.ui.screens.ConsoleScaffold
import com.example.ui.screens.LandingScreen
import com.example.ui.theme.PulseRelayTheme
import com.example.ui.viewmodel.PulseConsoleViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }
            PulseRelayTheme(darkTheme = isDarkTheme) {
                PulseRelayApp(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

@Composable
fun PulseRelayApp(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    authOverride: FirebaseAuth? = null
) {
    val context = LocalContext.current
    val auth = remember(context, authOverride) {
        authOverride ?: runCatching {
            if (com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()) Firebase.auth else null
        }.getOrNull()
    }
    if (auth == null) {
        LandingScreen(
            isAuthenticated = false,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onEnterConsole = {},
            onAuthSuccess = {}
        )
        return
    }
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var showPublicSite by rememberSaveable { mutableStateOf(true) }

    // Silent auto sign-in on startup if an authorized Google account is already linked
    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onAuthSuccess = { showPublicSite = false },
            onUnauthenticated = { /* Stay on public LandingScreen */ }
        )
    }

    val user = currentUser
    if (user == null) {
        LandingScreen(
            isAuthenticated = false,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onEnterConsole = {},
            onAuthSuccess = { showPublicSite = false }
        )
    } else {
        val uid = user.uid
        key(uid) {
            val consoleViewModel: PulseConsoleViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        PulseConsoleViewModel(
                            repository = PulseRepository(db),
                            currentUserId = uid,
                            appContext = app.applicationContext
                        )
                    }
                }
            )

            if (showPublicSite) {
                LandingScreen(
                    isAuthenticated = true,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onEnterConsole = { showPublicSite = false },
                    onAuthSuccess = { showPublicSite = false }
                )
            } else {
                BackHandler { showPublicSite = true }
                ConsoleScaffold(
                    viewModel = consoleViewModel,
                    userEmail = user.email?.takeIf { it.isNotBlank() } ?: user.phoneNumber,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onShowPublicSite = { showPublicSite = true }
                )
            }
        }
    }
}
