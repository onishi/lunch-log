package app.lunchlog.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import app.lunchlog.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

sealed interface SignInState {
    data object SignedOut : SignInState
    data object InProgress : SignInState
    data object SignedIn : SignInState
    data class Failed(val message: String) : SignInState
}

/**
 * Google ログイン (SPEC F-301)。
 *
 * Credential Manager で ID トークンを取り、Firebase Auth に渡す。
 * 失敗しても理由を画面に出すだけで、アプリは落とさない。
 */
class SignInViewModel(private val auth: FirebaseAuth) : ViewModel() {

    private val _state = MutableStateFlow<SignInState>(
        if (auth.currentUser != null) SignInState.SignedIn else SignInState.SignedOut,
    )
    val state: StateFlow<SignInState> = _state.asStateFlow()

    suspend fun signIn(context: Context) {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isEmpty()) {
            // 設定漏れを黙って失敗させない (README のセットアップ手順を参照)。
            _state.value = SignInState.Failed("ウェブクライアント ID が未設定です")
            return
        }

        _state.value = SignInState.InProgress
        try {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(false)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

            val response = CredentialManager.create(context).getCredential(context, request)
            val idToken = GoogleIdTokenCredential.createFrom(response.credential.data).idToken

            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            _state.value = SignInState.SignedIn
        } catch (error: Exception) {
            _state.value = SignInState.Failed("ログインできませんでした: ${error.message}")
        }
    }
}
