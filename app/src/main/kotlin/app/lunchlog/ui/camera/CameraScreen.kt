package app.lunchlog.ui.camera

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 撮影画面 (SPEC F-101)。
 *
 * 迷わせないため、出すのはプレビューとシャッターだけ。
 * 権限が無いときは**理由を説明してから** OS のダイアログを出す (SPEC §10)。
 */
@Composable
fun CameraScreen(onCaptured: (Uri) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }

    if (!hasPermission) {
        PermissionRationale(
            onRequest = { requestPermission.launch(Manifest.permission.CAMERA) },
            onCancel = onCancel,
        )
        return
    }

    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PreviewView(viewContext).also { previewView ->
                    val providerFuture = ProcessCameraProvider.getInstance(viewContext)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build()
                            .also { it.surfaceProvider = previewView.surfaceProvider }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                    }, ContextCompat.getMainExecutor(viewContext))
                }
            },
        )

        Button(
            onClick = {
                capture(context, imageCapture) { uri -> uri?.let(onCaptured) }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
        ) {
            Text("撮影")
        }
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit, onCancel: () -> Unit) {
    LaunchedEffect(Unit) { /* 説明を見せてから要求する。自動では出さない。 */ }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement
            .spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("カメラを使います", style = MaterialTheme.typography.titleMedium)
        Text(
            "食べたものを撮って記録するために使います。撮った写真はあなたのアカウントにだけ保存されます。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRequest) { Text("カメラを許可する") }
        androidx.compose.material3.TextButton(onClick = onCancel) { Text("写真を選んで記録する") }
    }
}

private fun capture(context: Context, imageCapture: ImageCapture, onResult: (Uri?) -> Unit) {
    val file = File(context.cacheDir, "capture-${System.currentTimeMillis()}.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    val executor: Executor = ContextCompat.getMainExecutor(context)

    imageCapture.takePicture(
        options,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onResult(output.savedUri ?: Uri.fromFile(file))
            }

            override fun onError(exception: ImageCaptureException) {
                // 撮影に失敗しても画面は閉じない。もう一度シャッターを押せばよい。
                onResult(null)
            }
        },
    )
}
