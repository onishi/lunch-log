package app.lunchlog.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lunchlog.core.model.MealType
import app.lunchlog.core.validation.RecordError
import coil.compose.AsyncImage
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 記録の編集 (SPEC §9.1)。
 *
 * 保存に必須なのは「写真 1 枚」か「メニュー名」のどちらか一方だけ。
 * 店名・日時・食事種別は既定で埋まっているので、確認して保存するだけで終わる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditScreen(
    viewModel: RecordEditViewModel,
    onSaved: () -> Unit,
    onTakePhoto: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::addPhoto)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "記録する" else "編集") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("戻る") } },
                actions = {
                    TextButton(onClick = { viewModel.save(onSaved) }, enabled = !state.saving) {
                        Text("保存")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PhotoRow(
                paths = state.photos.mapNotNull { it.thumbPath ?: it.localPath },
                onTakePhoto = onTakePhoto,
                onPickPhoto = {
                    pickPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )

            OutlinedTextField(
                value = state.dishName,
                onValueChange = viewModel::setDishName,
                label = { Text("メニュー名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.restaurantName,
                onValueChange = viewModel::setRestaurantName,
                label = { Text("店名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SuggestionRow(state, viewModel)

            MealTypeRow(state.mealType, viewModel::setMealType)

            Text(
                text = state.eatenAt.atZone(ZoneId.systemDefault()).format(DATE_TIME_FORMAT),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.errors.forEach { error ->
                Text(error.message(), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PhotoRow(paths: List<String>, onTakePhoto: () -> Unit, onPickPhoto: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (paths.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(paths) { path ->
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onTakePhoto) { Text("撮影") }
            OutlinedButton(onClick = onPickPhoto) { Text("写真を選ぶ") }
        }
    }
}

@Composable
private fun SuggestionRow(state: RecordEditState, viewModel: RecordEditViewModel) {
    if (state.loadingSuggestions) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp))
            Text("近くのお店を探しています", style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    if (state.suggestions.isEmpty()) return

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.suggestions) { suggestion ->
            AssistChip(
                onClick = { viewModel.applySuggestion(suggestion) },
                label = {
                    val distance = suggestion.distanceMeters
                    Text(if (distance != null) "${suggestion.name} ${distance}m" else suggestion.name)
                },
            )
        }
    }
}

@Composable
private fun MealTypeRow(selected: MealType, onSelect: (MealType) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MealType.entries) { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text(type.label()) },
            )
        }
    }
}

private fun MealType.label(): String = when (this) {
    MealType.LUNCH -> "ランチ"
    MealType.BREAKFAST -> "朝食"
    MealType.DINNER -> "夕食"
    MealType.CAFE -> "カフェ"
    MealType.SNACK -> "間食"
    MealType.OTHER -> "その他"
}

private fun RecordError.message(): String = when (this) {
    RecordError.EMPTY_RECORD -> "写真かメニュー名のどちらかを入力してください"
    RecordError.RATING_OUT_OF_RANGE -> "評価は 1〜5 で入力してください"
    RecordError.NEGATIVE_PRICE -> "金額が不正です"
    RecordError.DISH_NAME_TOO_LONG -> "メニュー名が長すぎます"
    RecordError.RESTAURANT_NAME_TOO_LONG -> "店名が長すぎます"
    RecordError.MEMO_TOO_LONG -> "メモが長すぎます"
    RecordError.TOO_MANY_PHOTOS -> "写真は 10 枚までです"
}

private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
