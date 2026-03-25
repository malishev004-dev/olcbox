package org.turnbox.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PingViewModel : ViewModel() {
    var uiState by mutableStateOf<PingState>(PingState.Idle)
        private set

    fun performPing() {
        viewModelScope.launch {
            uiState = PingState.Loading
            try {
                delay(2000)
                uiState = PingState.Success(latency = 87)
            } catch (e: Exception) {
                uiState = PingState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class PingState {
    object Idle : PingState()
    object Loading : PingState()
    data class Success(val latency: Long) : PingState()
    data class Error(val message: String) : PingState()
}

@Composable
fun PingButton(
    modifier: Modifier = Modifier,
    viewModel: PingViewModel = viewModel()
) {
    val state = viewModel.uiState

    val descriptionText = when (state) {
        is PingState.Error -> "Error: ${state.message}"
        is PingState.Loading -> "Checking..."
        is PingState.Success -> "Success: Ping ${state.latency}ms"
        else -> "Click To Verify Reachability"
    }

    val stateIcon: @Composable () -> Unit = {
        when (state) {
            is PingState.Error -> Icon(
                imageVector = Icons.Rounded.PriorityHigh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )

            is PingState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    strokeWidth = 2.5.dp
                )
            }

            is PingState.Success -> Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )

            else -> Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { viewModel.performPing() }
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (state is PingState.Error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Connectivity Check",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = descriptionText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = if (state is PingState.Error) {
                    MaterialTheme.colorScheme.onTertiary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    stateIcon()
                }
            }
        }
    }
}
