package app.lunchlog.ui.auth

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * ログイン画面 (SPEC F-301)。ボタンはひとつだけ。
 */
@Composable
fun SignInScreen(viewModel: SignInViewModel, onSignedIn: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context: Context = LocalContext.current
    val scope = rememberCoroutineScope()

    // コンポジション中に画面遷移を起こさない (再コンポーズのたびに走ってしまう)。
    LaunchedEffect(state) {
        if (state is SignInState.SignedIn) onSignedIn()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ランチログ", style = MaterialTheme.typography.headlineMedium)
        Text(
            "毎日のランチを記録します",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (val current = state) {
            is SignInState.InProgress -> CircularProgressIndicator()
            is SignInState.Failed -> Text(current.message, color = MaterialTheme.colorScheme.error)
            else -> Unit
        }

        Button(
            onClick = { scope.launch { viewModel.signIn(context) } },
            enabled = state !is SignInState.InProgress,
        ) {
            Text("Google でログイン")
        }
    }
}
