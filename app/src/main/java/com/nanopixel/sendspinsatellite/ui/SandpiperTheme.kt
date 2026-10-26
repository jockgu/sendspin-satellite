package com.nanopixel.sendspinsatellite.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.colorResource
import com.nanopixel.sendspinsatellite.R

@Composable
internal fun SandpiperTheme(content: @Composable () -> Unit) {
    val midnight = colorResource(R.color.midnight_violet)
    val indigo = colorResource(R.color.indigo_velvet)
    val steel = colorResource(R.color.steel_blue)
    val frozen = colorResource(R.color.frozen_water)
    val honeydew = colorResource(R.color.honeydew)
    val dark = isSystemInDarkTheme()
    val colors = remember(dark, midnight, indigo, steel, frozen, honeydew) {
        if (dark) {
            darkColorScheme().copy(
                primary = frozen, onPrimary = midnight,
                primaryContainer = indigo, onPrimaryContainer = honeydew,
                secondary = steel, onSecondary = midnight,
                secondaryContainer = indigo, onSecondaryContainer = honeydew,
                tertiary = frozen, onTertiary = midnight,
                tertiaryContainer = indigo, onTertiaryContainer = honeydew,
                onBackground = honeydew, onSurface = honeydew, onSurfaceVariant = frozen,
                outline = steel, outlineVariant = steel,
                inverseOnSurface = midnight, inversePrimary = indigo,
            )
        } else {
            lightColorScheme().copy(
                primary = indigo, onPrimary = honeydew,
                primaryContainer = frozen, onPrimaryContainer = midnight,
                secondary = indigo, onSecondary = honeydew,
                secondaryContainer = steel, onSecondaryContainer = midnight,
                tertiary = indigo, onTertiary = honeydew,
                tertiaryContainer = frozen, onTertiaryContainer = midnight,
                onBackground = midnight, onSurface = midnight, onSurfaceVariant = indigo,
                outline = indigo, outlineVariant = indigo,
                inverseOnSurface = honeydew, inversePrimary = frozen,
            )
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
