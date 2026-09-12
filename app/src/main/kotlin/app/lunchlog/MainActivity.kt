package app.lunchlog

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.lunchlog.ui.ViewModelFactory
import app.lunchlog.ui.auth.SignInScreen
import app.lunchlog.ui.auth.SignInViewModel
import app.lunchlog.ui.camera.CameraScreen
import app.lunchlog.ui.detail.RecordDetailScreen
import app.lunchlog.ui.detail.RecordDetailViewModel
import app.lunchlog.ui.edit.RecordEditScreen
import app.lunchlog.ui.edit.RecordEditViewModel
import app.lunchlog.ui.home.HomeScreen
import app.lunchlog.ui.home.HomeViewModel
import app.lunchlog.ui.search.SearchScreen
import app.lunchlog.ui.search.SearchViewModel
import app.lunchlog.ui.theme.LunchLogTheme

private object Routes {
    const val SIGN_IN = "sign-in"
    const val HOME = "home"
    const val CAMERA = "camera"
    const val SEARCH = "search"

    /** recordId が空なら新規作成。画面は同じものを使い回す (SPEC §9.1)。 */
    const val EDIT = "edit?recordId={recordId}&sharedUrl={sharedUrl}"
    const val DETAIL = "detail/{recordId}"

    fun edit(recordId: String? = null, sharedUrl: String? = null): String {
        val encoded = sharedUrl?.let { Uri.encode(it) }.orEmpty()
        return "edit?recordId=${recordId.orEmpty()}&sharedUrl=$encoded"
    }

    fun detail(recordId: String) = "detail/$recordId"
}

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 共有されてきた URL。食べログのページから「共有」した場合に入る (SPEC §6.3)。
        val sharedText = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)

        setContent {
            LunchLogTheme {
                val locator = remember { ServiceLocator.from(applicationContext) }
                val navController = rememberNavController()
                val signedIn = locator.auth.currentUser != null
                val startDestination = when {
                    !signedIn -> Routes.SIGN_IN
                    // 共有で起動したときは、URL を入れた新規記録を直接開く。
                    sharedText != null -> Routes.edit(sharedUrl = sharedText)
                    else -> Routes.HOME
                }

                LunchLogNavHost(locator, navController, startDestination)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LunchLogNavHost(
    locator: ServiceLocator,
    navController: NavHostController,
    startDestination: String,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SIGN_IN) {
            val viewModel: SignInViewModel = viewModel(factory = ViewModelFactory(locator))
            SignInScreen(viewModel) {
                navController.navigate(Routes.HOME) { popUpTo(Routes.SIGN_IN) { inclusive = true } }
            }
        }

        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(factory = ViewModelFactory(locator))
            HomeScreen(
                viewModel = viewModel,
                onAddRecord = { navController.navigate(Routes.edit()) },
                onOpenRecord = { navController.navigate(Routes.detail(it)) },
                onSearch = { navController.navigate(Routes.SEARCH) },
            )
        }

        composable(
            route = Routes.EDIT,
            arguments = listOf(
                navArgument("recordId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("sharedUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            // 編集画面は撮影画面と行き来するため、ViewModel を NavBackStackEntry に紐づけて
            // 戻ってきたときに入力内容を保つ。
            val viewModel: RecordEditViewModel = viewModel(
                viewModelStoreOwner = entry,
                factory = ViewModelFactory(locator),
            )
            val editingId = entry.arguments?.getString("recordId").orEmpty()
            val sharedUrl = entry.arguments?.getString("sharedUrl").orEmpty()
            LaunchedEffect(editingId) {
                if (editingId.isEmpty()) {
                    viewModel.startNew()
                    if (sharedUrl.isNotEmpty()) viewModel.setTabelogUrl(sharedUrl)
                } else {
                    viewModel.load(editingId)
                }
            }

            // 撮影画面から戻るときに Uri を受け取る。
            val capturedUri = entry.savedStateHandle.get<String>(CAPTURED_URI_KEY)
            LaunchedEffect(capturedUri) {
                capturedUri?.let {
                    viewModel.addPhoto(Uri.parse(it))
                    entry.savedStateHandle.remove<String>(CAPTURED_URI_KEY)
                }
            }

            RecordEditScreen(
                viewModel = viewModel,
                onSaved = { navController.popBackStack() },
                onTakePhoto = { navController.navigate(Routes.CAMERA) },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(Routes.SEARCH) {
            val viewModel: SearchViewModel = viewModel(factory = ViewModelFactory(locator))
            SearchScreen(
                viewModel = viewModel,
                onOpenRecord = { navController.navigate(Routes.detail(it)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CAMERA) {
            CameraScreen(
                onCaptured = { uri ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(CAPTURED_URI_KEY, uri.toString())
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("recordId") { type = NavType.StringType }),
        ) { entry ->
            val recordId = requireNotNull(entry.arguments?.getString("recordId"))
            val viewModel: RecordDetailViewModel =
                viewModel(factory = ViewModelFactory(locator, recordId))

            RecordDetailScreen(
                viewModel = viewModel,
                onEdit = { navController.navigate(Routes.edit(recordId)) },
                onDeleted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private const val CAPTURED_URI_KEY = "captured-uri"
