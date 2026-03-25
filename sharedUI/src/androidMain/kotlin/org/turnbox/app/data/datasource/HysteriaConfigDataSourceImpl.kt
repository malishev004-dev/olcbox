package org.turnbox.app.data.datasource

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.turnbox.app.data.MASTER_HYSTERIA_CONFIG_FILE_NAME
import org.turnbox.app.data.model.HysteriaConfig
import org.turnbox.app.data.model.TurnConfig
import org.turnbox.app.vpn.data.KEY_IS_VPN_CONFIG_READY
import org.turnbox.app.vpn.data.KEY_SELECTED_TURN_TYPE
import org.turnbox.app.vpn.data.KEY_VPN_CONFIG_PATH
import org.turnbox.app.vpn.data.vpnPrefDataStore
import java.io.File

val KEY_SELECTED_HYSTERIA_ID = stringPreferencesKey("selected_hysteria_id")

class HysteriaConfigDataSourceImpl(
    private val context: Context
) : HysteriaConfigDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun saveHysteriaConfig(config: HysteriaConfig, id: String): Unit =
        withContext(Dispatchers.IO) {
            val hysteriaJsonFile = File(context.filesDir, "hysteria_settings_$id.json")
            hysteriaJsonFile.writeText(json.encodeToString(HysteriaConfig.serializer(), config))

            if (getSelectedHysteriaId() == id) {
                updateVpnConfigFile(config, loadTurnConfig(getSelectedTurnType()))
            }
        }

    override suspend fun loadHysteriaConfig(id: String): HysteriaConfig =
        withContext(Dispatchers.IO) {
            if (id.isBlank()) return@withContext HysteriaConfig()
            val hysteriaJsonFile = File(context.filesDir, "hysteria_settings_$id.json")
            if (!hysteriaJsonFile.exists()) return@withContext HysteriaConfig()
            try {
                return@withContext json.decodeFromString(
                    HysteriaConfig.serializer(),
                    hysteriaJsonFile.readText()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext HysteriaConfig()
            }
        }

    override suspend fun saveTurnConfig(config: TurnConfig, type: String): Unit =
        withContext(Dispatchers.IO) {
            val turnJsonFile = File(context.filesDir, "turn_settings_$type.json")
            turnJsonFile.writeText(json.encodeToString(TurnConfig.serializer(), config))

            if (getSelectedTurnType() == type) {
                updateVpnConfigFile(loadHysteriaConfig(getSelectedHysteriaId()), config)
            }
        }

    override suspend fun loadTurnConfig(type: String): TurnConfig = withContext(Dispatchers.IO) {
        val turnJsonFile = File(context.filesDir, "turn_settings_$type.json")
        if (!turnJsonFile.exists()) {
            return@withContext when (type) {
                "vk" -> TurnConfig(
                    enabled = true,
                    link = "https://vk.com/call/join/dQw4w9WgXcQ",
                    threads = 8,
                    udp = true
                )

                "yandex" -> TurnConfig(
                    enabled = true,
                    link = "https://telemost.yandex.ru/j/12345678901234",
                    threads = 8,
                    udp = true
                )

                else -> TurnConfig()
            }
        }
        try {
            return@withContext json.decodeFromString(
                TurnConfig.serializer(),
                turnJsonFile.readText()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext TurnConfig()
        }
    }

    private suspend fun updateVpnConfigFile(hysteria: HysteriaConfig, turn: TurnConfig) {
        val vpnConfigFile = File(context.filesDir, MASTER_HYSTERIA_CONFIG_FILE_NAME)
        vpnConfigFile.writeText(hysteria.getFullConfig(turn))

        context.vpnPrefDataStore.edit {
            it[KEY_IS_VPN_CONFIG_READY] = true
            it[KEY_VPN_CONFIG_PATH] = vpnConfigFile.absolutePath
        }
    }

    override suspend fun saveRawConfig(text: String): Unit = withContext(Dispatchers.IO) {
        val vpnConfigFile = File(context.filesDir, MASTER_HYSTERIA_CONFIG_FILE_NAME)
        vpnConfigFile.writeText(text)

        context.vpnPrefDataStore.edit {
            it[KEY_IS_VPN_CONFIG_READY] = true
            it[KEY_VPN_CONFIG_PATH] = vpnConfigFile.absolutePath
        }
        Unit
    }

    override suspend fun getSelectedTurnType(): String {
        return context.vpnPrefDataStore.data.first()[KEY_SELECTED_TURN_TYPE] ?: "custom"
    }

    override suspend fun setSelectedTurnType(type: String) {
        context.vpnPrefDataStore.edit {
            it[KEY_SELECTED_TURN_TYPE] = type
        }
        updateVpnConfigFile(loadHysteriaConfig(getSelectedHysteriaId()), loadTurnConfig(type))
    }

    override suspend fun getSelectedHysteriaId(): String {
        return context.vpnPrefDataStore.data.first()[KEY_SELECTED_HYSTERIA_ID] ?: ""
    }

    override suspend fun setSelectedHysteriaId(id: String) {
        context.vpnPrefDataStore.edit {
            it[KEY_SELECTED_HYSTERIA_ID] = id
        }
        if (id.isNotBlank()) {
            updateVpnConfigFile(loadHysteriaConfig(id), loadTurnConfig(getSelectedTurnType()))
        }
    }

    override suspend fun getAllHysteriaConfigs(): List<Pair<String, HysteriaConfig>> =
        withContext(Dispatchers.IO) {
            val files = context.filesDir.listFiles { file ->
                file.name.startsWith("hysteria_settings_") && file.name.endsWith(".json")
            } ?: emptyArray()

            files.map { file ->
                val id = file.name.removePrefix("hysteria_settings_").removeSuffix(".json")
                val config = try {
                    json.decodeFromString(HysteriaConfig.serializer(), file.readText())
                } catch (e: Exception) {
                    HysteriaConfig()
                }
                id to config
            }
        }

    override suspend fun deleteHysteriaConfig(id: String): Unit = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "hysteria_settings_$id.json")
        if (file.exists()) file.delete()

        if (getSelectedHysteriaId() == id) {
            setSelectedHysteriaId("")
        }
    }
}
