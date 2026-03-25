package org.turnbox.app.data.repository

import org.turnbox.app.data.model.HysteriaConfig
import org.turnbox.app.data.model.TurnConfig

interface HysteriaConfigRepository {
    suspend fun saveHysteriaConfig(config: HysteriaConfig, id: String = "default")
    suspend fun loadHysteriaConfig(id: String = "default"): HysteriaConfig
    suspend fun saveTurnConfig(config: TurnConfig, type: String = "custom")
    suspend fun loadTurnConfig(type: String = "custom"): TurnConfig
    suspend fun saveRawConfig(text: String)
    suspend fun getSelectedTurnType(): String
    suspend fun setSelectedTurnType(type: String)
    suspend fun getSelectedHysteriaId(): String
    suspend fun setSelectedHysteriaId(id: String)
    suspend fun getAllHysteriaConfigs(): List<Pair<String, HysteriaConfig>>
    suspend fun deleteHysteriaConfig(id: String)
}
