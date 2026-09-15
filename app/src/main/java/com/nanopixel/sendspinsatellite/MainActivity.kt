package com.nanopixel.sendspinsatellite

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanopixel.sendspinsatellite.connection.ConnectionViewModel
import com.nanopixel.sendspinsatellite.ui.SendspinSatelliteApp

class MainActivity : ComponentActivity() {
    private val connectionViewModel: ConnectionViewModel by viewModels()
    private var pendingLocalNetworkAction: (() -> Unit)? = null
    private val localNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingLocalNetworkAction
        pendingLocalNetworkAction = null
        if (granted) action?.invoke() else connectionViewModel.localNetworkPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by connectionViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                withLocalNetworkPermission(connectionViewModel::start)
            }
            SendspinSatelliteApp(
                state = state,
                onServerAddressChanged = connectionViewModel::updateServerAddress,
                onPlayerNameChanged = connectionViewModel::updatePlayerName,
                onConnect = { withLocalNetworkPermission(connectionViewModel::connect) },
                onDiscover = { withLocalNetworkPermission(connectionViewModel::discover) },
                onSelectDiscoveredServer = connectionViewModel::selectDiscoveredServer,
                onDisconnect = connectionViewModel::disconnect,
                onForgetServer = connectionViewModel::forgetServer,
            )
        }
    }

    private fun withLocalNetworkPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
            checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
            return
        }
        pendingLocalNetworkAction = action
        localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }
}
