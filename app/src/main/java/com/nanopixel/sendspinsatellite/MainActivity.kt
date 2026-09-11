package com.nanopixel.sendspinsatellite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanopixel.sendspinsatellite.connection.ConnectionViewModel
import com.nanopixel.sendspinsatellite.ui.SendspinSatelliteApp

class MainActivity : ComponentActivity() {
    private val connectionViewModel: ConnectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by connectionViewModel.uiState.collectAsStateWithLifecycle()
            SendspinSatelliteApp(
                state = state,
                onServerAddressChanged = connectionViewModel::updateServerAddress,
                onPlayerNameChanged = connectionViewModel::updatePlayerName,
                onConnect = connectionViewModel::connect,
                onDisconnect = connectionViewModel::disconnect,
            )
        }
    }
}
