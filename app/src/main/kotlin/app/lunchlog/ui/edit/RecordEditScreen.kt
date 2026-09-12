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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lunchlog.core.model.MealType
import app.lunchlog.core.model.PhotoKind
import app.lunchlog.core.ocr.MenuTextParser
import app.lunchlog.core.tabelog.TabelogUrl
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

    val pickMenuPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.addPhoto(it, PhotoKind.MENU) }
    }

    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onLocationPermissionResult(granted) }

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
                onPickMenuPhoto = {
                    pickMenuPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )

            if (state.menuCandidates.isNotEmpty()) {
                MenuCandidateRow(state.menuCandidates, viewModel::applyMenuCandidate)
            }

            OutlinedTextField(
                value = state.dishName,
                onValueChange = viewModel::setDishName,
                label = { Text("メニュー名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.dishSuggestions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.dishSuggestions) { name ->
                        AssistChip(onClick = { viewModel.setDishName(name) }, label = { Text(name) })
                    }
                }
            }

            OutlinedTextField(
                value = state.restaurantName,
                onValueChange = viewModel::setRestaurantName,
                label = { Text("店名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.needsLocationRationale) {
                LocationRationale(
                    onAllow = { requestLocation.launch(android.Manifest.permission.ACCESS_FINE_LOCATION) },
                    onSkip = viewModel::dismissLocationRationale,
                )
            }

            SuggestionRow(state, viewModel)

            MealTypeRow(state.mealType, viewModel::setMealType)

            OutlinedTextField(
                value = state.price,
                onValueChange = viewModel::setPrice,
                label = { Text("金額") },
                suffix = { Text("円") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            RatingRow(state.rating, viewModel::setRating)

            OutlinedTextField(
                value = state.memo,
                onValueChange = viewModel::setMemo,
                label = { Text("メモ") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            TagRow(state.tags, viewModel::addTag, viewModel::removeTag)

            OutlinedTextField(
                value = state.tabelogUrl,
                onValueChange = viewModel::setTabelogUrl,
                label = { Text("食べログ URL") },
                singleLine = true,
                isError = state.tabelogWarning == TabelogUrl.Warning.NOT_TABELOG,
                supportingText = state.tabelogWarning?.let { warning -> { Text(warning.message()) } },
                modifier = Modifier.fillMaxWidth(),
            )

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
private fun PhotoRow(
    paths: List<String>,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickMenuPhoto: () -> Unit,
) {
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
            OutlinedButton(onClick = onPickMenuPhoto) { Text("メニュー表") }
        }
    }
}

/**
 * 位置情報の使い道の説明 (SPEC §10)。
 *
 * OS のダイアログをいきなり出さない。断られても記録は手入力で続けられるため、
 * 「あとで」を同じ大きさで置く。
 */
@Composable
private fun LocationRationale(onAllow: () -> Unit, onSkip: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("近くのお店から店名を埋められます", style = MaterialTheme.typography.titleSmall)
            Text(
                "位置情報は記録するときだけ使います。許可しなくても、履歴から店を選んだり手で入力したりできます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAllow) { Text("位置情報を許可") }
                OutlinedButton(onClick = onSkip) { Text("あとで") }
            }
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

/** OCR で読み取ったメニュー候補 (SPEC §6.2)。押すとメニュー名と金額が入る。 */
@Composable
private fun MenuCandidateRow(
    candidates: List<MenuTextParser.MenuCandidate>,
    onSelect: (MenuTextParser.MenuCandidate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("メニュー表から読み取りました", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(candidates) { candidate ->
                AssistChip(
                    onClick = { onSelect(candidate) },
                    label = {
                        val price = candidate.price
                        Text(if (price != null) "${candidate.name} ${price}円" else candidate.name)
                    },
                )
            }
        }
    }
}

/** 5 段階評価 (SPEC F-108)。同じ星をもう一度押すと解除できる。 */
@Composable
private fun RatingRow(rating: Int?, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("評価", style = MaterialTheme.typography.bodyMedium)
        (1..5).forEach { star ->
            TextButton(onClick = { onSelect(star) }) {
                Text(if (rating != null && star <= rating) "★" else "☆")
            }
        }
    }
}

@Composable
private fun TagRow(tags: List<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var input by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tags.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tags) { tag ->
                    AssistChip(onClick = { onRemove(tag) }, label = { Text("#$tag ×") })
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("タグを追加") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onAdd(input)
                input = ""
            }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun TabelogUrl.Warning.message(): String = when (this) {
    TabelogUrl.Warning.NOT_TABELOG -> "食べログの URL ではないようです"
    TabelogUrl.Warning.NOT_HTTPS -> "https ではありません"
    TabelogUrl.Warning.MALFORMED -> "URL として読めません"
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
