package app.lunchlog.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lunchlog.core.model.LunchRecord
import coil.compose.AsyncImage
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ホーム (SPEC F-201)。日付ごとのセクションに記録カードを並べる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddRecord: () -> Unit,
    onOpenRecord: (String) -> Unit,
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("ランチログ") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddRecord, text = { Text("記録する") }, icon = {})
        },
    ) { padding ->
        if (sections.isEmpty()) {
            EmptyState(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sections.forEach { section ->
                item(key = "header-${section.date}") { DayHeader(section.date) }
                items(section.records, key = { it.id }) { record ->
                    RecordCard(record, onClick = { onOpenRecord(record.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("まだ記録がありません", style = MaterialTheme.typography.titleMedium)
        Text(
            "右下のボタンから今日のランチを記録できます",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    Text(
        text = date.format(DATE_FORMAT),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RecordCard(record: LunchRecord, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumb = record.coverPhoto?.let { it.thumbPath ?: it.localPath }
            if (thumb != null && File(thumb).exists()) {
                AsyncImage(
                    model = File(thumb),
                    contentDescription = record.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.displayTitle ?: "(名前なし)",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                record.restaurantName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = record.eatenAt.atZone(ZoneId.systemDefault()).format(TIME_FORMAT),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 (E)")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
