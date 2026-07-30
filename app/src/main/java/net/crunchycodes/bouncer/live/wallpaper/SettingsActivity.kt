package net.crunchycodes.bouncer.live.wallpaper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.crunchycodes.bouncer.live.wallpaper.ui.theme.BouncerScreenSaverTheme
import kotlin.math.roundToInt

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
    private fun SettingsScreen(settings: SettingsManager) {
        // Keep slider state local while dragging so the UI stays responsive; persist only
        // when the gesture completes to avoid writing preferences on every frame.
        var ballCount by remember { mutableFloatStateOf(settings.ballCount.toFloat()) }
        var ballSpeed by remember { mutableFloatStateOf(settings.ballSpeed) }
        var selectedPalette by remember { mutableStateOf(settings.palette) }
        var physicsEnabled by remember { mutableStateOf(settings.physicsEnabled) }
        var sizeBehavior by remember { mutableFloatStateOf(settings.sizeBehavior) }
        var lifespanBase by remember { mutableFloatStateOf(settings.lifespanBase) }
        var destroyOnTouch by remember { mutableStateOf(settings.destroyOnTouch) }
        val deviceMaxBallCount = settings.effectiveMaxBallCount()
        val deviceMaxBallSpeed = settings.effectiveMaxBallSpeed()
        val detectedRefreshRateHz = settings.calibrationRefreshRateHz.roundToInt()

        val scrollState = rememberScrollState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = stringResource(R.string.solid_body_physics))
                    Switch(
                        checked = physicsEnabled,
                        onCheckedChange = {
                            physicsEnabled = it
                            settings.physicsEnabled = it
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = stringResource(R.string.destroy_on_touch))
                    Switch(
                        checked = destroyOnTouch,
                        onCheckedChange = {
                            destroyOnTouch = it
                            settings.destroyOnTouch = it
                        },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(
                        R.string.calibrated_device_cap,
                        deviceMaxBallCount,
                        detectedRefreshRateHz,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(text = stringResource(R.string.ball_count_label, ballCount.toInt()))
                Slider(
                    value = ballCount,
                    onValueChange = { ballCount = it },
                    onValueChangeFinished = { settings.ballCount = ballCount.toInt() },
                    valueRange = BouncerPhysics.MIN_BALL_COUNT.toFloat()..deviceMaxBallCount.toFloat(),
                )
                Text(
                    text = stringResource(R.string.high_ball_count_note, deviceMaxBallCount),
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        settings.resetCalibration()
                        startActivity(android.content.Intent(this@SettingsActivity, MainActivity::class.java))
                        finish()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.rerun_calibration))
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = { finishAffinity() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.exit_app))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = stringResource(R.string.ball_speed_label, ballSpeed))
                Slider(
                    value = ballSpeed,
                    onValueChange = { ballSpeed = it },
                    onValueChangeFinished = { settings.ballSpeed = ballSpeed },
                    valueRange = BouncerPhysics.MIN_BALL_SPEED..deviceMaxBallSpeed,
                )
                Text(
                    text = stringResource(R.string.ball_speed_cap_note, deviceMaxBallSpeed),
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(modifier = Modifier.height(16.dp))

                val sizeText = when {
                    sizeBehavior < -0.1f -> stringResource(R.string.size_behavior_shrink, -sizeBehavior)
                    sizeBehavior > 0.1f -> stringResource(R.string.size_behavior_grow, sizeBehavior)
                    else -> stringResource(R.string.size_behavior_static)
                }
                Text(text = stringResource(R.string.size_behavior_label, sizeText))
                Slider(
                    value = sizeBehavior,
                    onValueChange = { sizeBehavior = it },
                    onValueChangeFinished = { settings.sizeBehavior = sizeBehavior },
                    valueRange = BouncerPhysics.MIN_SIZE_BEHAVIOR..BouncerPhysics.MAX_SIZE_BEHAVIOR,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = stringResource(R.string.lifespan_label, lifespanBase.toInt()))
                Slider(
                    value = lifespanBase,
                    onValueChange = { lifespanBase = it },
                    onValueChangeFinished = { settings.lifespanBase = lifespanBase },
                    valueRange = BouncerPhysics.MIN_LIFESPAN_SECONDS..BouncerPhysics.MAX_LIFESPAN_SECONDS,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = stringResource(R.string.color_palette))
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ColorPalette.entries.forEach { palette ->
                        FilterChip(
                            selected = selectedPalette == palette,
                            onClick = {
                                selectedPalette = palette
                                settings.palette = palette
                            },
                            label = { Text(stringResource(palette.labelRes)) },
                        )
                    }
                }
            }
        }
    }
}
