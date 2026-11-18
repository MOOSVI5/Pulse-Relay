package com.example.data.firebase

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@SuppressLint("DiscouragedApi")
private fun resolveDefaultWebClientId(context: Context): String? {
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (resId == 0) return null
    return runCatching { context.getString(resId) }.getOrNull()?.takeIf { it.isNotBlank() }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit
) {
    if (FirebaseApp.getApps(context).isEmpty()) {
        onUnauthenticated()
        return
    }
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = resolveDefaultWebClientId(context)
    if (clientId == null) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit = {}
) {
    if (FirebaseApp.getApps(context).isEmpty()) {
        onAuthError("Firebase is not configured in this build (google-services.json missing). Add GOOGLE_SERVICES_JSON in GitHub Secrets or download the APK from AI Studio.")
        return
    }
    val clientId = resolveDefaultWebClientId(context)
    if (clientId == null) {
        onAuthError("Google Sign-In configuration missing: google-services.json / default_web_client_id not found")
        return
    }
    val activity = context as? Activity
    if (activity == null) {
        onAuthError("Activity context required for Google Sign-In")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type returned")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed")
        }
    }
}

fun sendPhoneSignInOtp(
    context: Context,
    phoneNumberE164: String,
    scope: CoroutineScope,
    resendToken: PhoneAuthProvider.ForceResendingToken? = null,
    onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken) -> Unit,
    onAutoVerified: () -> Unit,
    onError: (String) -> Unit
) {
    if (FirebaseApp.getApps(context).isEmpty()) {
        onError("Firebase is not configured in this build (google-services.json missing). Add GOOGLE_SERVICES_JSON in GitHub Secrets or download the APK from AI Studio.")
        return
    }
    val cleaned = phoneNumberE164.trim().replace(" ", "").replace("-", "")
    if (!Regex("^\\+[1-9]\\d{6,14}$").matches(cleaned)) {
        onError("Enter a valid cell number in E.164 format (e.g. +14155550199).")
        return
    }
    val activity = context as? Activity
    if (activity == null) {
        onError("Activity context required for phone verification.")
        return
    }

    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            scope.launch {
                try {
                    Firebase.auth.signInWithCredential(credential).await()
                    onAutoVerified()
                } catch (e: Exception) {
                    onError(e.localizedMessage ?: "Auto verification sign-in failed.")
                }
            }
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.e("Auth", "Phone verification failed", e)
            val msg = e.localizedMessage.orEmpty()
            val errorCode = (e as? FirebaseAuthException)?.errorCode.orEmpty()
            val friendly = when {
                errorCode == "ERROR_OPERATION_NOT_ALLOWED" ||
                    msg.contains("not allowed", ignoreCase = true) ||
                    msg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ->
                    "Enable Phone Sign-In in Firebase Console (Authentication → Sign-in method → Phone) for this project."
                msg.contains("app is not authorized", ignoreCase = true) ||
                    msg.contains("Play Integrity", ignoreCase = true) ->
                    "App SHA-1/SHA-256 fingerprint is missing or Play Integrity failed. Ensure DEBUG_KEYSTORE_BASE64 matches Firebase."
                else -> msg.ifBlank { "Phone verification failed." }
            }
            onError(friendly)
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            onCodeSent(verificationId, token)
        }
    }

    val builder = PhoneAuthOptions.newBuilder(Firebase.auth)
        .setPhoneNumber(cleaned)
        .setTimeout(60L, TimeUnit.SECONDS)
        .setActivity(activity)
        .setCallbacks(callbacks)
    if (resendToken != null) {
        builder.setForceResendingToken(resendToken)
    }
    PhoneAuthProvider.verifyPhoneNumber(builder.build())
}

fun verifyPhoneSignInOtp(
    verificationId: String,
    smsCode: String,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val code = smsCode.trim()
    if (code.length < 4) {
        onError("Enter the 6-digit SMS code sent to your phone.")
        return
    }
    scope.launch {
        try {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            Firebase.auth.signInWithCredential(credential).await()
            onAuthSuccess()
        } catch (e: Exception) {
            Log.e("Auth", "Phone OTP sign-in failed", e)
            onError(e.localizedMessage ?: "Invalid or expired SMS code.")
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onBeforeSignOut: () -> Unit = {},
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            onBeforeSignOut()
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
