package org.turnbox.app.androidApp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.turnbox.app.data.datasource.HysteriaConfigDataSourceImpl
import org.turnbox.app.data.datasource.HysteriaConfigRepositoryImpl
import org.turnbox.app.data.importer.AndroidConfigImporter
import org.turnbox.app.ui.activities.AndroidMainScreen
import org.turnbox.app.ui.features.home.HomeScreenViewModel
import org.turnbox.app.ui.features.locations.LocationViewModel
import org.turnbox.app.ui.theme.AppTheme
import org.turnbox.app.vpn.AndroidVpnManager

class AppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vpnManager = AndroidVpnManager(this)
        val configDataSource = HysteriaConfigDataSourceImpl(this)
        val configRepository = HysteriaConfigRepositoryImpl(configDataSource)
        val configImporter = AndroidConfigImporter(this)

        val viewModel = HomeScreenViewModel(
            vpnManager = vpnManager,
            configRepo = configRepository,
            configImporter = configImporter
        )
        
        val locationViewModel = LocationViewModel(
            configRepo = configRepository
        )

        enableEdgeToEdge()
        setContent {
            AppTheme({ 
                AndroidMainScreen(
                    viewModel = viewModel,
                    locationViewModel = locationViewModel
                ) 
            })
        }
    }
}
