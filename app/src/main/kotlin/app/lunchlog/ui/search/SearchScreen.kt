package app.lunchlog.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lunchlog.core.model.MealType
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 検索画面 (SPEC F-205, F-206)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenRecord: (String) -> Unit,
    onBack: () -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("検索") },
                navigationIcon = { TextButton(onClick = onBack) { Text("戻る") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query.text,
                onValueChange = viewModel::setText,
                label = { Text("店名・メニュー・メモ") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MealType.entries) { mealType ->
                    FilterChip(
                        selected = mealType in query.mealTypes,
                        onClick = { viewModel.toggleMealType(mealType) },
                        label = { Text(mealType.label()) },
                    )
                }
            }

            if (results.isEmpty()) {
                Text(
                    "見つかりませんでした",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(results, key = { it.id }) { record ->
                    ListItem(
                        headlineContent = { Text(record.displayTitle ?: "(名前なし)") },
                        supportingContent = {
                            val date = record.eatenAt.atZone(ZoneId.systemDefault()).format(DATE_FORMAT)
                            Text(listOfNotNull(record.restaurantName, date).joinToString(" · "))
                        },
                        modifier = Modifier.clickable { onOpenRecord(record.id) },
                    )
                }
            }
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

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")
