package net.crunchycodes.bouncer.live.wallpaper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.crunchycodes.bouncer.live.wallpaper.ui.theme.BouncerScreenSaverTheme
import java.util.Locale

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsManager(this)
        setContent {
            BouncerScreenSaverTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SettingsScreen(settings)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsScreen(settings: SettingsManager) {
        var ballCount by remember { mutableFloatStateOf(settings.ballCount.toFloat()) }
        var ballSpeed by remember { mutableFloatStateOf(settings.ballSpeed) }
        var selectedPalette by remember { mutableStateOf(settings.palette) }
        var physicsEnabled by remember { mutableStateOf(settings.physicsEnabled) }
        var sizeBehavior by remember { mutableFloatStateOf(settings.sizeBehavior) }
        var lifespanBase by remember { mutableFloatStateOf(settings.lifespanBase) }
        var destroyOnTouch by remember { mutableStateOf(settings.destroyOnTouch) }

        val palettes = listOf("Random", "Neon", "Ocean", "Fire", "Pastel", "Forest")
        val scrollState = rememberScrollState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Wallpaper Settings") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(text = "Solid Body Physics")
                    Switch(
                        checked = physicsEnabled,
                        onCheckedChange = {
                            physicsEnabled = it
                            settings.physicsEnabled = it
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(text = "Destroy on Touch")
                    Switch(
                        checked = destroyOnTouch,
                        onCheckedChange = {
                            destroyOnTouch = it
                            settings.destroyOnTouch = it
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Ball Count: ${ballCount.toInt()}")
                Slider(
                    value = ballCount,
                    onValueChange = { 
                        ballCount = it
                        settings.ballCount = it.toInt()
                    },
                    valueRange = 1f..1000f
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Base Speed: ${String.format(Locale.getDefault(), "%.1f", ballSpeed)}")
                Slider(
                    value = ballSpeed,
                    onValueChange = { 
                        ballSpeed = it
                        settings.ballSpeed = it
                    },
                    valueRange = 1f..20f
                )

                Spacer(modifier = Modifier.height(16.dp))

                val sizeText = when {
                    sizeBehavior < -0.1f -> "Shrink (Speed: ${String.format(Locale.getDefault(), "%.1f", -sizeBehavior)})"
                    sizeBehavior > 0.1f -> "Grow (Speed: ${String.format(Locale.getDefault(), "%.1f", sizeBehavior)})"
                    else -> "Static Size"
                }
                Text(text = "Size Behavior: $sizeText")
                Slider(
                    value = sizeBehavior,
                    onValueChange = {
                        sizeBehavior = it
                        settings.sizeBehavior = it
                    },
                    valueRange = -2f..2f
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Avg Lifespan: ${lifespanBase.toInt()}s")
                Slider(
                    value = lifespanBase,
                    onValueChange = {
                        lifespanBase = it
                        settings.lifespanBase = it
                    },
                    valueRange = 2f..300f
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Color Palette")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    palettes.forEach { palette ->
                        FilterChip(
                            selected = selectedPalette == palette,
                            onClick = {
                                selectedPalette = palette
                                settings.palette = palette
                            },
                            label = { Text(palette) }
                        )
                    }
                }
            }
        }
    }
}
