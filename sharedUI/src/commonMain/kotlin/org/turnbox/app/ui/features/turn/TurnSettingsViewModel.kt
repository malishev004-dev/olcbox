package org.turnbox.app.ui.features.turn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.turnbox.app.data.model.TurnConfig
import org.turnbox.app.data.repository.HysteriaConfigRepository


data class CustomTurnState(
    val address: String = "",
    val port: String = "3478",
    val username: String = "",
    val password: String = "",
    val threads: Int = 8,
    val isCheckingConnectivity: Boolean = false,
    val connectivityResult: ConnectivityStatus = ConnectivityStatus.Idle
)

sealed class ConnectivityStatus {
    object Idle : ConnectivityStatus()
    object Success : ConnectivityStatus()
    data class Error(val message: String) : ConnectivityStatus()
}

class CustomTurnViewModel(
    private val configRepository: HysteriaConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomTurnState())
    val uiState = _uiState.asStateFlow()

    fun onAddressChanged(value: String) {
        _uiState.update { it.copy(address = value) }
    }

    fun onPortChanged(value: String) {
        _uiState.update { it.copy(port = value) }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun onThreadsChanged(value: Int) {
        _uiState.update { it.copy(threads = value) }
    }

    fun checkConnectivity() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingConnectivity = true) }
            delay(1500)
            _uiState.update {
                it.copy(
                    isCheckingConnectivity = false,
                    connectivityResult = ConnectivityStatus.Success
                )
            }
        }
    }

    fun saveSettings() {
        val currentState = _uiState.value
        viewModelScope.launch {
            configRepository.saveTurnConfig(
                TurnConfig(
                    listen = "${currentState.address}:${currentState.port}",
                    threads = currentState.threads
                )
            )
        }
    }

    fun deleteSettings() {
        _uiState.value = CustomTurnState()
    }
}