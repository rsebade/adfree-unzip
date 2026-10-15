package com.rsebade.adfreeunzip.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rsebade.adfreeunzip.R
import com.rsebade.adfreeunzip.ui.theme.AppTheme

/** Stateful entry point: wires the ViewModel to the stateless [HomeScreen]. */
@Composable
fun HomeRoute(viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(uiState = uiState, onIncrement = viewModel::onIncrement)
}

/** Stateless screen: render [uiState], report events upward. Previewable and testable in isolation. */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.home_greeting, uiState.name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = pluralStringResource(R.plurals.home_count, uiState.count, uiState.count),
                modifier = Modifier.testTag(HomeTestTags.COUNT),
            )
            Button(onClick = onIncrement, modifier = Modifier.testTag(HomeTestTags.INCREMENT)) {
                Text(stringResource(R.string.home_increment))
            }
        }
    }
}

object HomeTestTags {
    const val COUNT = "home_count"
    const val INCREMENT = "home_increment"
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    AppTheme {
        HomeScreen(uiState = HomeUiState(count = 3), onIncrement = {})
    }
}
