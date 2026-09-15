package com.nanopixel.sendspinsatellite.connection

import android.content.Context
import androidx.core.content.edit

/** The one Sendspin server this device should use automatically. */
data class SavedServer(
    val address: String,
    val name: String? = null,
)

/** Small shared owner for connection settings used by both the UI and playback service. */
internal class ConnectionPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun savedServer(): SavedServer? {
        val address = preferences.getString(LAST_WORKING_SERVER_KEY, null)?.trim().orEmpty()
        if (!address.startsWith("ws://")) return null
        return SavedServer(
            address = address,
            name = preferences.getString(LAST_WORKING_SERVER_NAME_KEY, null)?.trim()?.ifBlank { null },
        )
    }

    fun saveServer(server: SavedServer) {
        val address = server.address.trim()
        if (!address.startsWith("ws://")) return
        preferences.edit {
            putString(LAST_WORKING_SERVER_KEY, address)
            if (server.name.isNullOrBlank()) {
                remove(LAST_WORKING_SERVER_NAME_KEY)
            } else {
                putString(LAST_WORKING_SERVER_NAME_KEY, server.name.trim())
            }
        }
    }

    fun forgetServer() {
        preferences.edit {
            remove(LAST_WORKING_SERVER_KEY)
            remove(LAST_WORKING_SERVER_NAME_KEY)
        }
    }

    fun playerName(): String? = preferences.getString(PLAYER_NAME_KEY, null)

    fun savePlayerName(name: String) {
        preferences.edit { putString(PLAYER_NAME_KEY, name) }
    }

    private companion object {
        const val PREFERENCES_NAME = "sendspin_settings"
        const val LAST_WORKING_SERVER_KEY = "last_working_server"
        const val LAST_WORKING_SERVER_NAME_KEY = "last_working_server_name"
        const val PLAYER_NAME_KEY = "player_name"
    }
}
