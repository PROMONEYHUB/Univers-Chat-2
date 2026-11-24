package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val PHONE_TAG = "PhoneAuth"
private const val GOOGLE_TAG = "Auth"

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
        onUnauthenticated()
        return
    }
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
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
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    val activity = context.findActivity()
    if (activity == null) {
        onAuthError("Unable to access the active screen for Google Sign-In.")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type returned by Google Sign-In.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(GOOGLE_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: NoCredentialException) {
            Log.w(GOOGLE_TAG, "No Google credentials available: ${e.message}", e)
            onAuthError("No Google account found on this device. Add one in Settings or sign in with your phone number.")
        } catch (e: GetCredentialException) {
            Log.e(GOOGLE_TAG, "Google Sign-In credential error", e)
            onAuthError(e.localizedMessage ?: "Google Sign-In failed. Please try again.")
        } catch (e: Exception) {
            Log.e(GOOGLE_TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Google Sign-In failed")
        }
    }
}

fun formatE164PhoneNumber(dialCode: String, rawPhone: String): String {
    val trimmed = rawPhone.trim()
    if (trimmed.startsWith("+")) {
        return "+" + trimmed.drop(1).filter { it.isDigit() }
    }
    val codeDigits = dialCode.filter { it.isDigit() }
    val rawDigits = trimmed.filter { it.isDigit() }
    // Preserve trunk 0 in Italy/Vatican; trim leading national trunk 0 elsewhere
    val normalizedLocal = if (codeDigits == "39" || codeDigits == "379") {
        rawDigits
    } else {
        rawDigits.trimStart('0')
    }
    // Avoid duplicating 4-digit NANP area codes (e.g., +1242 + 2425551234 -> +12425551234)
    val finalLocal = if (
        codeDigits.length == 4 &&
        codeDigits.startsWith("1") &&
        normalizedLocal.length == 10 &&
        normalizedLocal.startsWith(codeDigits.drop(1))
    ) {
        normalizedLocal.drop(3)
    } else {
        normalizedLocal
    }
    return "+$codeDigits$finalLocal"
}

fun isValidE164PhoneNumber(e164: String): Boolean {
    if (!e164.startsWith("+")) return false
    val digits = e164.drop(1)
    return digits.length in 7..15 && digits.all { it.isDigit() }
}

fun startPhoneNumberVerification(
    activity: Activity,
    phoneNumberE164: String,
    resendToken: PhoneAuthProvider.ForceResendingToken? = null,
    onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken) -> Unit,
    onAutoVerified: (credential: PhoneAuthCredential) -> Unit,
    onVerificationFailed: (message: String) -> Unit
) {
    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            Log.d(PHONE_TAG, "Phone verification auto-completed")
            onAutoVerified(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.w(PHONE_TAG, "Phone verification failed: ${e.message}", e)
            onVerificationFailed(mapPhoneAuthException(e))
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            Log.d(PHONE_TAG, "OTP code sent successfully")
            onCodeSent(verificationId, token)
        }
    }

    val builder = PhoneAuthOptions.newBuilder(Firebase.auth)
        .setPhoneNumber(phoneNumberE164)
        .setTimeout(60L, TimeUnit.SECONDS)
        .setActivity(activity)
        .setCallbacks(callbacks)

    if (resendToken != null) {
        builder.setForceResendingToken(resendToken)
    }

    PhoneAuthProvider.verifyPhoneNumber(builder.build())
}

suspend fun verifyPhoneOtpAndSignIn(
    verificationId: String,
    otpCode: String,
    displayName: String?
): Result<Unit> {
    val credential = PhoneAuthProvider.getCredential(verificationId, otpCode.trim())
    return signInWithPhoneCredential(credential, displayName)
}

object AuthProfilePrefill {
    @Volatile
    var pendingDisplayName: String? = null
}

suspend fun signInWithPhoneCredential(
    credential: PhoneAuthCredential,
    displayName: String?
): Result<Unit> {
    val cleanedName = displayName?.trim()?.take(60)?.takeIf { it.isNotBlank() }
    AuthProfilePrefill.pendingDisplayName = cleanedName
    return runCatching {
        val result = Firebase.auth.signInWithCredential(credential).await()
        val user = result.user
        if (user != null && cleanedName != null && user.displayName.isNullOrBlank()) {
            runCatching {
                user.updateProfile(
                    userProfileChangeRequest {
                        this.displayName = cleanedName
                    }
                ).await()
            }
        }
    }.onFailure {
        AuthProfilePrefill.pendingDisplayName = null
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(GOOGLE_TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

fun mapPhoneAuthException(e: Throwable): String {
    if (e is FirebaseTooManyRequestsException) {
        return "Too many verification attempts. Please wait a few minutes and try again."
    }
    if (e is FirebaseAuthMissingActivityForRecaptchaException) {
        return "Unable to open verification check. Please try again."
    }
    if (e is FirebaseAuthInvalidCredentialsException) {
        return when (e.errorCode) {
            "ERROR_INVALID_VERIFICATION_CODE" ->
                "That 6-digit code doesn't match. Check your SMS and try again."
            "ERROR_SESSION_EXPIRED" ->
                "Your verification code expired. Tap Resend OTP for a new code."
            else ->
                "Invalid phone number or verification credential (${e.errorCode}). Use full country code (e.g. +91 98765 43210)."
        }
    }
    if (e is FirebaseAuthException) {
        Log.e(PHONE_TAG, "FirebaseAuthException code=${e.errorCode}: ${e.localizedMessage}", e)
        val msg = e.localizedMessage.orEmpty()
        return when (e.errorCode) {
            "ERROR_OPERATION_NOT_ALLOWED" -> mapOperationNotAllowedMessage(msg)
            "ERROR_APP_NOT_AUTHORIZED" ->
                "This APK's signing fingerprint isn't registered in Firebase (ERROR_APP_NOT_AUTHORIZED). Add the APK's SHA-1 and SHA-256 in Firebase Console → Project Settings → Your apps."
            "ERROR_INVALID_APP_CREDENTIAL" ->
                "Play Integrity / reCAPTCHA app check failed (ERROR_INVALID_APP_CREDENTIAL). Register this APK's SHA-256 in Firebase Console → Project Settings → Your apps, or use a test phone number."
            "ERROR_MISSING_CLIENT_IDENTIFIER",
            "ERROR_CAPTCHA_CHECK_FAILED" ->
                "reCAPTCHA / Play Integrity verification failed (${e.errorCode}). Check Google Play Services & SHA-256 fingerprint in Firebase Console."
            "ERROR_QUOTA_EXCEEDED" ->
                "SMS quota exceeded for this project (${e.errorCode}). Wait a while or upgrade your Firebase billing plan."
            "ERROR_USER_DISABLED" ->
                "This account has been disabled."
            else -> {
                if (msg.contains("BILLING_NOT_ENABLED", ignoreCase = true)) {
                    "Real SMS OTP requires the Firebase Blaze billing plan (BILLING_NOT_ENABLED). Upgrade billing in Firebase Console, or add a Test Phone Number under Authentication → Phone."
                } else {
                    "[${e.errorCode}] ${msg.ifBlank { "Verification failed. Please try again." }}"
                }
            }
        }
    }
    return e.localizedMessage ?: "Verification failed. Please check your connection and try again."
}

internal fun mapOperationNotAllowedMessage(detail: String?): String {
    val msg = detail.orEmpty()
    return when {
        msg.contains("region", ignoreCase = true) ||
            msg.contains("SMS unable to be sent", ignoreCase = true) ->
            "SMS OTP is blocked for this country by your Firebase SMS region policy. Enable this country (or Allow all regions) in Firebase Console → Authentication → Settings → SMS region policy."
        msg.contains("BILLING_NOT_ENABLED", ignoreCase = true) ->
            "Real SMS OTP requires the Firebase Blaze billing plan (BILLING_NOT_ENABLED). Upgrade billing in Firebase Console, or add a Test Phone Number under Authentication → Phone."
        else ->
            "Phone sign-in is disabled for this project (ERROR_OPERATION_NOT_ALLOWED). Enable Authentication → Sign-in method → Phone in Firebase Console."
    }
}
