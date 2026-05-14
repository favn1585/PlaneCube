package com.plane.cube.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.plane.cube.domain.entity.ItemStatus
import com.plane.cube.features.home.model.HomeItemModel
import com.plane.cube.features.home.model.HomeUiIntent
import com.plane.cube.features.home.model.HomeViewState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val viewState by viewModel.viewState.collectAsState()
    HomeContent(state = viewState, onIntent = viewModel::onUiIntent)
}

@Composable
private fun HomeContent(
    state: HomeViewState,
    onIntent: (HomeUiIntent) -> Unit,
) {
    when {
        state.items == null && state.errorMessage == null -> LoadingState()
        state.errorMessage != null && state.items.isNullOrEmpty() ->
            ErrorState(message = state.errorMessage, onRetry = { onIntent(HomeUiIntent.OnPullToRefresh) })
        else -> ItemsList(
            items = state.items.orEmpty(),
            onIntent = onIntent,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun ItemsList(
    items: List<HomeItemModel>,
    onIntent: (HomeUiIntent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            ItemRow(item = item, onClick = { onIntent(HomeUiIntent.OnItemClicked(item.id)) })
        }
    }
}

@Composable
private fun ItemRow(
    item: HomeItemModel,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = item.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = item.status.name, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Preview
@Composable
private fun HomeContentPreview() {
    HomeContent(
        state = HomeViewState(
            items = listOf(
                HomeItemModel("1", "First", "Sample description", ItemStatus.NEW),
                HomeItemModel("2", "Second", "Another description", ItemStatus.ACTIVE),
            ),
        ),
        onIntent = {},
    )
}
