package us.leaf3stones.hy2droid.proxy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.turnbox.app.data.TUN2SOCKS_CONFIG_FILE_NAME
import org.turnbox.app.data.model.TurnConfig
import org.turnbox.app.data.repository.HysteriaConfigRepository
import org.turnbox.app.vpn.data.KEY_IS_VPN_CONFIG_READY
import org.turnbox.app.vpn.data.KEY_VPN_CONFIG_PATH
import org.turnbox.app.vpn.data.vpnPrefDataStore
import java.io.File
import java.util.Scanner
import kotlin.concurrent.thread

class Hysteria2VpnService : VpnService() {

    private external fun startTun2socks(configPath: String, fd: Int)
    private external fun stopTun2socks()
    private external fun getTun2socksStats(): LongArray

    private var netFileDescriptor: ParcelFileDescriptor? = null
    private var hysteriaProcess: Process? = null
    private var hysteriaLoggingThread: Thread? = null
    private var turnProcess: Process? = null
    private var turnLoggingThread: Thread? = null

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private val startMutex = Mutex()

    private var startupJob: Job? = null
    private var lastConfigPath: String? = null
    private var lastMigrationTime: Long = 0L
    private var isRunning = false

    private lateinit var connectivityManager: ConnectivityManager
    private var currentNetwork: Network? = null
    private var isCallbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            handleNetworkChange(network, "Available")
        }

        override fun onLost(network: Network) {
            addLog("❌ Network LOST detected")
            val active = connectivityManager.activeNetwork
            if (active != null && active != network) {
                handleNetworkChange(active, "Fallback after LOSS")
            } else {
                addLog("⚠️ No active network after loss — waiting...")
                scope.launch {
                    delay(1500)
                    val a = connectivityManager.activeNetwork
                    if (a != null) handleNetworkChange(a, "Delayed fallback")
                }
            }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (currentNetwork == network) {
                handleNetworkChange(network, "Capabilities changed")
            }
        }

        private fun handleNetworkChange(network: Network, reason: String) {
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) return

            val netName = getNetName(caps)
            val isNew = currentNetwork != network

            if (isNew || reason.contains("Fallback") || reason.contains("LOSS")) {
                addLog("🔄 $reason: $netName")

                currentNetwork = network
                setUnderlyingNetworks(arrayOf(network))
                try {
                    connectivityManager.bindProcessToNetwork(network)
                    addLog("✅ Bound to $netName")
                } catch (e: Exception) {
                    Log.w(TAG, "bind failed", e)
                }

                val now = System.currentTimeMillis()
                if (now - lastMigrationTime < 3000) {
                    addLog("⏳ Migration throttled")
                    return
                }
                lastMigrationTime = now

                lastConfigPath?.let { startVpnChecked(true, it, isMigration = true) }
            }
        }

        private fun getNetName(caps: NetworkCapabilities): String = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            else -> "Other"
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isStart = intent?.action == ACTION_START_VPN
        if (!isStart) {
            cleanup(); stopSelf(); return START_NOT_STICKY
        }

        if (isRunning && netFileDescriptor != null) return START_STICKY

        startForeground()
        scope.launch {
            val pref = vpnPrefDataStore.data.first()
            val ready = pref[KEY_IS_VPN_CONFIG_READY] ?: false
            val path = pref[KEY_VPN_CONFIG_PATH] ?: ""

            connectivityManager.bindProcessToNetwork(null)

            registerNetworkMonitor()
            startVpnChecked(ready, path, isMigration = false)
        }
        return START_STICKY
    }

    private fun registerNetworkMonitor() {
        if (isCallbackRegistered) return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            isCallbackRegistered = true
            addLog("📡 Network monitor registered")
        } catch (e: Exception) {
            Log.e(TAG, "Monitor error", e)
        }
    }

    private fun startForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel("CHANNEL_ID", "Turnbox VPN", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                channel
            )
        }
        val notif = NotificationCompat.Builder(this, "CHANNEL_ID")
            .setContentTitle("Turnbox")
            .setContentText("Protecting your connection")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, 100, notif,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
    }

    private fun startVpnChecked(
        isConfigReady: Boolean,
        configPath: String,
        isMigration: Boolean = false
    ) {
        if (!isConfigReady || configPath.isBlank()) return
        lastConfigPath = configPath

        startupJob?.cancel()
        startupJob = scope.launch {
            startMutex.withLock {
                addLog(if (isMigration) "🔄 Reconnecting tunnel..." else "🚀 Starting VPN...")

                try {
                    stopTun2socks()
                } catch (_: Exception) {
                }

                stopTransportProcesses()
                delay(1000)

                val repo = configRepository ?: run {
                    addLog("❌ ConfigRepository is NULL")
                    return@withLock
                }

                val selectedTurnType = repo.getSelectedTurnType()
                val baseTurnConfig = repo.loadTurnConfig(selectedTurnType)
                val selectedHysteriaId = repo.getSelectedHysteriaId()
                val hysteriaConfig = repo.loadHysteriaConfig(selectedHysteriaId)

                val turnConfig = baseTurnConfig.copy(peer = hysteriaConfig.server)

                if (turnConfig.enabled) {
                    startTurnInternal(turnConfig)
                    delay(if (isMigration) 1500L else 3000L)
                }

                startHysteriaInternal(configPath)
                delay(500)

                netFileDescriptor?.close()
                val fd = establishSystemVpnTunnel()
                if (fd != -1) {
                    startTun2socks(File(filesDir, TUN2SOCKS_CONFIG_FILE_NAME).absolutePath, fd)
                    isRunning = true
                    _isConnected.value = true
                    addLog("✅ VPN Tunnel established")
                }
            }
        }
    }

    private fun startTurnInternal(config: TurnConfig) {
        addLog("🚀 Starting turn tunnel with peer: ${config.peer}")
        val cmd = mutableListOf<String>().apply {
            add(File(applicationInfo.nativeLibraryDir, "libvkturn.so").absolutePath)
            add("-peer"); add(config.peer)
            if (config.link.isNotBlank()) {
                add(if (config.link.contains("yandex")) "-yandex-link" else "-vk-link")
                add(config.link)
            }
            add("-listen"); add(config.listen)
            add("-n"); add(config.threads.toString())
            if (config.udp) add("-udp")
            if (config.noDtls) add("-no-dtls")
        }
        turnProcess = ProcessBuilder(cmd).redirectErrorStream(true).start()
        turnLoggingThread = thread(name = "TurnLog") {
            try {
                Scanner(turnProcess?.inputStream).use { sc ->
                    while (sc.hasNextLine()) {
                        val line = sc.nextLine()
                        if (line.contains("Established")) addLog("✅ TURN OK")
                        Log.v("vk-turn", line)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun startHysteriaInternal(configPath: String) {
        val cmd = listOf(
            File(applicationInfo.nativeLibraryDir, "libhysteria.so").absolutePath,
            "-c",
            configPath
        )
        hysteriaProcess = ProcessBuilder(cmd).redirectErrorStream(true).start()
        hysteriaLoggingThread = thread(name = "HyLog") {
            try {
                Scanner(hysteriaProcess?.inputStream).use { sc ->
                    while (sc.hasNextLine()) {
                        val line = sc.nextLine()
                        if (line.contains("connected")) addLog("✅ HY2 Connected")
                        Log.v("hysteria", line)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun establishSystemVpnTunnel(): Int {
        val builder = Builder()
            .setMtu(1250)
            .addAddress("10.0.88.88", 16)
            .addDnsServer("1.1.1.1")
            .addDisallowedApplication(packageName)
            .addRoute("0.0.0.0", 0)

        listOf(
            "com.vkontakte.android", "ru.yandex.searchplugin", "ru.yandex.yandexbrowser",
            "com.yandex.browser", "com.android.vending", "com.google.android.gms",
            "com.android.captiveportallogin"
        ).forEach {
            try {
                builder.addDisallowedApplication(it)
            } catch (_: Exception) {
            }
        }

        currentNetwork?.let { builder.setUnderlyingNetworks(arrayOf(it)) }

        val pfd = builder.establish()
        netFileDescriptor = pfd
        return pfd?.fd ?: -1
    }

    private fun stopTransportProcesses() {
        addLog("🛑 Stopping transport processes...")

        hysteriaProcess?.destroy()
        hysteriaProcess = null
        hysteriaLoggingThread?.interrupt()
        hysteriaLoggingThread = null

        turnProcess?.destroy()
        turnProcess = null
        turnLoggingThread?.interrupt()
        turnLoggingThread = null
    }

    private fun cleanup() {
        isRunning = false
        _isConnected.value = false
        startupJob?.cancel()
        scope.launch {
            startMutex.withLock {
                try {
                    stopTun2socks()
                } catch (_: Exception) {
                }
                stopTransportProcesses()
                netFileDescriptor?.close(); netFileDescriptor = null
            }
        }
        if (isCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {
            }
            isCallbackRegistered = false
        }
        connectivityManager.bindProcessToNetwork(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    companion object {
        init {
            System.loadLibrary("tun2socks")
        }

        const val ACTION_START_VPN =
            "us.leaf3stones.hy2droid.proxy.Hysteria2VpnService.ACTION_START_VPN"
        const val ACTION_STOP_VPN =
            "us.leaf3stones.hy2droid.proxy.Hysteria2VpnService.ACTION_STOP_VPN"

        private val _logs = MutableStateFlow<List<String>>(emptyList())
        val logs = _logs.asStateFlow()

        private val _isConnected = MutableStateFlow(false)
        val isConnected = _isConnected.asStateFlow()

        var configRepository: HysteriaConfigRepository? = null

        fun addLog(msg: String) {
            Log.d("Hysteria2VpnService", msg)
            _logs.update { (it + msg).takeLast(120) }
        }

        private const val TAG = "Hysteria2VpnService"
    }
}
