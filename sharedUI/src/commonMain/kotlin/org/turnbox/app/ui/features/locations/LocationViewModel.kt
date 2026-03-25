package org.turnbox.app.ui.features.locations

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.turnbox.app.data.model.HysteriaConfig
import org.turnbox.app.data.repository.HysteriaConfigRepository

data class LocationItem(
    val id: String,
    val name: String,
    val flagEmoji: String,
    val config: HysteriaConfig? = null
)

sealed class PingsState {
    object Idle : PingsState()
    data class Loading(val lastPings: Map<String, Int>? = null) : PingsState()
    data class Success(val pings: Map<String, Int>) : PingsState()
    data class Error(val message: String) : PingsState()
}

class LocationViewModel(
    private val configRepo: HysteriaConfigRepository
) : ViewModel() {

    var locations = mutableStateListOf<LocationItem>()
        private set

    var selectedLocationId by mutableStateOf<String?>(null)
        private set

    var pingsState by mutableStateOf<PingsState>(PingsState.Idle)
        private set

    // For editing
    var editingConfig by mutableStateOf(HysteriaConfig())
    var editingName by mutableStateOf("")
    var editingId by mutableStateOf<String?>(null)

    init {
        loadLocations()
    }

    fun loadLocations() {
        viewModelScope.launch {
            val savedConfigs = configRepo.getAllHysteriaConfigs()
            val currentSelectedId = configRepo.getSelectedHysteriaId()

            locations.clear()

            savedConfigs.forEach { (id, config) ->
                val displayName = config.name.ifBlank { config.server }
                locations.add(LocationItem(id, displayName, "📍", config))
            }

            if (locations.isNotEmpty() && (currentSelectedId.isBlank() || locations.none { it.id == currentSelectedId })) {
                val nextId = locations.firstOrNull()?.id
                configRepo.setSelectedHysteriaId(nextId ?: "")
                selectedLocationId = nextId
            } else {
                selectedLocationId = currentSelectedId.ifBlank { null }
            }
        }
    }

    fun selectLocation(id: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            configRepo.setSelectedHysteriaId(id)
            selectedLocationId = id
            onComplete()
        }
    }

    fun refreshPings() {
        val previousPings = (pingsState as? PingsState.Success)?.pings
        viewModelScope.launch {
            pingsState = PingsState.Loading(lastPings = previousPings)
            try {
                delay(1000)
                val fakePings = locations.associate { it.id to (10..90).random() }
                pingsState = PingsState.Success(fakePings)
            } catch (e: Exception) {
                pingsState = PingsState.Error(e.message ?: "Error")
            }
        }
    }

    fun startEditing(id: String?) {
        if (id == null) {
            editingId = null
            editingConfig = HysteriaConfig()
            editingName = ""
        } else {
            val location = locations.find { it.id == id }
            editingId = id
            editingConfig = location?.config ?: HysteriaConfig()
            editingName = location?.name ?: ""
        }
    }

    fun onNameChanged(value: String) {
        editingName = value
    }

    fun onServerChanged(value: String) {
        editingConfig = editingConfig.copy(server = value)
    }

    fun onSniChanged(value: String) {
        editingConfig = editingConfig.copy(sni = value)
    }

    fun onPasswordChanged(value: String) {
        editingConfig = editingConfig.copy(password = value)
    }

    fun saveEditing(onComplete: () -> Unit) {
        viewModelScope.launch {
            val id = editingId ?: "custom_${(100..999).random()}"
            val finalConfig = editingConfig.copy(name = editingName)
            configRepo.saveHysteriaConfig(finalConfig, id)
            configRepo.setSelectedHysteriaId(id)
            loadLocations()
            onComplete()
        }
    }

    fun deleteLocation(id: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            configRepo.deleteHysteriaConfig(id)
            loadLocations()
            onComplete()
        }
    }
}
