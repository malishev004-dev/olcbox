package org.turnbox.app.data.datasource

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.turnbox.app.data.model.HysteriaConfig
import org.turnbox.app.data.model.TurnConfig
import org.turnbox.app.data.repository.HysteriaConfigRepository

interface HysteriaConfigDataSource {
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

@Serializable
private data class ImportWrapper(
    val version: Int = 1,
    val hysteria: HysteriaSection? = null,
    val turn: TurnSection? = null
)

@Serializable
private data class HysteriaSection(
    val server: String = "",
    val password: String = "",
    val sni: String = "",
    val insecure: Boolean = true
)

@Serializable
private data class TurnSection(
    val enabled: Boolean = false,
    val peer: String = "",
    val link: String = "",
    val user: String = "",
    val pass: String = "",
    val threads: Int = 8,
    val udp: Boolean = true,
    val noDtls: Boolean = false,
    val listen: String = "127.0.0.1:9000"
)

class HysteriaConfigRepositoryImpl(
    private val dataSource: HysteriaConfigDataSource
) : HysteriaConfigRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun saveHysteriaConfig(config: HysteriaConfig, id: String) {
        dataSource.saveHysteriaConfig(config, id)
    }

    override suspend fun loadHysteriaConfig(id: String): HysteriaConfig {
        return dataSource.loadHysteriaConfig(id)
    }

    override suspend fun saveTurnConfig(config: TurnConfig, type: String) {
        dataSource.saveTurnConfig(config, type)
    }

    override suspend fun loadTurnConfig(type: String): TurnConfig {
        return dataSource.loadTurnConfig(type)
    }

    override suspend fun saveRawConfig(text: String) {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val wrapper = json.decodeFromString<ImportWrapper>(trimmed)

                if (wrapper.hysteria != null || wrapper.turn != null) {
                    val hConfig = HysteriaConfig(
                        server = wrapper.hysteria?.server ?: "",
                        password = wrapper.hysteria?.password ?: "",
                        sni = wrapper.hysteria?.sni ?: "",
                        insecure = wrapper.hysteria?.insecure ?: true
                    )
                    val tConfig = TurnConfig(
                        enabled = wrapper.turn?.enabled ?: false,
                        peer = wrapper.turn?.peer ?: "",
                        link = wrapper.turn?.link ?: "",
                        user = wrapper.turn?.user ?: "",
                        pass = wrapper.turn?.pass ?: "",
                        threads = wrapper.turn?.threads ?: 8,
                        udp = wrapper.turn?.udp ?: true,
                        noDtls = wrapper.turn?.noDtls ?: false,
                        listen = wrapper.turn?.listen ?: "127.0.0.1:9000"
                    )
                    
                    val newId = "Imported_${hConfig.server.take(10)}"
                    dataSource.saveHysteriaConfig(hConfig, newId)
                    dataSource.setSelectedHysteriaId(newId)
                    
                    dataSource.saveTurnConfig(tConfig, "custom")
                    dataSource.setSelectedTurnType("custom")
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        dataSource.saveRawConfig(text)
    }

    override suspend fun getSelectedTurnType(): String {
        return dataSource.getSelectedTurnType()
    }

    override suspend fun setSelectedTurnType(type: String) {
        dataSource.setSelectedTurnType(type)
    }

    override suspend fun getSelectedHysteriaId(): String {
        return dataSource.getSelectedHysteriaId()
    }

    override suspend fun setSelectedHysteriaId(id: String) {
        dataSource.setSelectedHysteriaId(id)
    }

    override suspend fun getAllHysteriaConfigs(): List<Pair<String, HysteriaConfig>> {
        return dataSource.getAllHysteriaConfigs()
    }

    override suspend fun deleteHysteriaConfig(id: String) {
        dataSource.deleteHysteriaConfig(id)
    }
}
