package app.lunchlog.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 記録の詳細 (SPEC F-204)。MVP では写真・店名・メニュー名・日時と、編集・削除だけ。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    viewModel: RecordDetailViewModel,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    var confirmingDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("記録") },
                navigationIcon = { TextButton(onClick = onBack) { Text("戻る") } },
                actions = {
                    TextButton(onClick = onEdit) { Text("編集") }
                    TextButton(onClick = { confirmingDelete = true }) { Text("削除") }
                },
            )
        },
    ) { padding ->
        val current = record ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            current.photos.forEach { photo ->
                val path = photo.localPath ?: photo.thumbPath
                if (path != null && File(path).exists()) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = current.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)),
                    )
                }
            }

            Text(current.displayTitle ?: "(名前なし)", style = MaterialTheme.typography.headlineSmall)
            current.restaurantName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            Text(
                current.eatenAt.atZone(ZoneId.systemDefault()).format(DATE_TIME_FORMAT),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            current.memo?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("この記録を削除しますか") },
            // 論理削除なので 30 日以内なら戻せる (SPEC F-112)。それを伝えて不安を減らす。
            text = { Text("30 日間はゴミ箱に残ります。") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(onDeleted) }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("やめる") } },
        )
    }
}

private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 (E) HH:mm")
