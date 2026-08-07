package net.crunchycodes.bouncer.live.wallpaper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
        SettingsScreenContent(
            initialState = SettingsScreenState(
                ballCount = settings.ballCount.toFloat(),
                ballSpeed = settings.ballSpeed,
                selectedPalette = settings.palette,
                selectedBallStyle = settings.ballStyle,
                brightness = settings.brightness,
                transparency = settings.transparency,
                physicsEnabled = settings.physicsEnabled,
                sizeBehavior = settings.sizeBehavior,
                lifespanBase = settings.lifespanBase,
                destroyOnTouch = settings.destroyOnTouch,
                deviceMaxBallCount = settings.effectiveMaxBallCount(),
                deviceMaxBallSpeed = settings.effectiveMaxBallSpeed(),
                detectedRefreshRateHz = settings.calibrationRefreshRateHz.roundToInt(),
            ),
            onBack = { finish() },
            onDone = { moveTaskToBack(true) },
            onPhysicsEnabledChange = { settings.physicsEnabled = it },
            onDestroyOnTouchChange = { settings.destroyOnTouch = it },
            onBallCountChangeFinished = { settings.ballCount = it.toInt() },
            onReRunCalibration = {
                settings.resetCalibration()
                startActivity(Intent(this@SettingsActivity, MainActivity::class.java))
                finish()
            },
            onBallSpeedChangeFinished = { settings.ballSpeed = it },
            onSizeBehaviorChangeFinished = { settings.sizeBehavior = it },
            onLifespanBaseChangeFinished = { settings.lifespanBase = it },
            onPaletteSelected = { settings.palette = it },
            onBallStyleSelected = { settings.ballStyle = it },
            onBrightnessChangeFinished = { settings.brightness = it },
            onTransparencyChangeFinished = { settings.transparency = it },
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScreenContent(
        initialState: SettingsScreenState,
        onBack: () -> Unit,
        onDone: () -> Unit,
        onPhysicsEnabledChange: (Boolean) -> Unit,
        onDestroyOnTouchChange: (Boolean) -> Unit,
        onBallCountChangeFinished: (Float) -> Unit,
        onReRunCalibration: () -> Unit,
        onBallSpeedChangeFinished: (Float) -> Unit,
        onSizeBehaviorChangeFinished: (Float) -> Unit,
        onLifespanBaseChangeFinished: (Float) -> Unit,
        onPaletteSelected: (ColorPalette) -> Unit,
        onBallStyleSelected: (BallStyle) -> Unit,
        onBrightnessChangeFinished: (Float) -> Unit,
        onTransparencyChangeFinished: (Float) -> Unit,
    ) {
        // Keep slider state local while dragging so the UI stays responsive; persist only
        // when the gesture completes to avoid writing preferences on every frame.
        var ballCount by remember { mutableFloatStateOf(initialState.ballCount) }
        var ballSpeed by remember { mutableFloatStateOf(initialState.ballSpeed) }
        var selectedPalette by remember { mutableStateOf(initialState.selectedPalette) }
        var selectedBallStyle by remember { mutableStateOf(initialState.selectedBallStyle) }
        var brightness by remember { mutableFloatStateOf(initialState.brightness) }
        var transparency by remember { mutableFloatStateOf(initialState.transparency) }
        var physicsEnabled by remember { mutableStateOf(initialState.physicsEnabled) }
        var sizeBehavior by remember { mutableFloatStateOf(initialState.sizeBehavior) }
        var lifespanBase by remember { mutableFloatStateOf(initialState.lifespanBase) }
        var destroyOnTouch by remember { mutableStateOf(initialState.destroyOnTouch) }
        var expandedSection by rememberSaveable {
            mutableStateOf<SettingsSection?>(SettingsSection.APPEARANCE)
        }
        val scrollState = rememberScrollState()
        val sliderModifier = Modifier
            .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Horizontal))
            .fillMaxWidth()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = onDone) {
                            Text(text = stringResource(R.string.done))
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsSectionCard(
                    title = stringResource(R.string.settings_section_appearance),
                    summary = stringResource(R.string.settings_section_appearance_summary),
                    expanded = expandedSection == SettingsSection.APPEARANCE,
                    onToggle = {
                        expandedSection = expandedSection.toggle(SettingsSection.APPEARANCE)
                    },
                ) {
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
                                    onPaletteSelected(palette)
                                },
                                label = { Text(stringResource(palette.labelRes)) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = stringResource(R.string.ball_style))
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BallStyle.entries.forEach { style ->
                            FilterChip(
                                selected = selectedBallStyle == style,
                                onClick = {
                                    selectedBallStyle = style
                                    onBallStyleSelected(style)
                                },
                                label = { Text(stringResource(style.labelRes)) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(selectedBallStyle.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.brightness_level,
                            (brightness * 100f).roundToInt(),
                        ),
                    )
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        onValueChangeFinished = { onBrightnessChangeFinished(brightness) },
                        valueRange = BallAppearance.MIN_BRIGHTNESS..BallAppearance.MAX_BRIGHTNESS,
                        modifier = sliderModifier,
                    )
                    Text(
                        text = stringResource(
                            R.string.transparency_level,
                            (transparency * 100f).roundToInt(),
                        ),
                    )
                    Slider(
                        value = transparency,
                        onValueChange = { transparency = it },
                        onValueChangeFinished = { onTransparencyChangeFinished(transparency) },
                        valueRange = BallAppearance.MIN_TRANSPARENCY..BallAppearance.MAX_TRANSPARENCY,
                        modifier = sliderModifier,
                    )
                }

                SettingsSectionCard(
                    title = stringResource(R.string.settings_section_motion),
                    summary = stringResource(R.string.settings_section_motion_summary),
                    expanded = expandedSection == SettingsSection.MOTION,
                    onToggle = { expandedSection = expandedSection.toggle(SettingsSection.MOTION) },
                ) {
                    Text(text = stringResource(R.string.ball_speed_label, ballSpeed))
                    Slider(
                        value = ballSpeed,
                        onValueChange = { ballSpeed = it },
                        onValueChangeFinished = { onBallSpeedChangeFinished(ballSpeed) },
                        valueRange = BouncerPhysics.MIN_BALL_SPEED..initialState.deviceMaxBallSpeed,
                        modifier = sliderModifier,
                    )
                    Text(
                        text = stringResource(R.string.ball_speed_cap_note, initialState.deviceMaxBallSpeed),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val sizeText = when {
                        sizeBehavior < -0.1f -> stringResource(R.string.size_behavior_shrink, -sizeBehavior)
                        sizeBehavior > 0.1f -> stringResource(R.string.size_behavior_grow, sizeBehavior)
                        else -> stringResource(R.string.size_behavior_static)
                    }
                    Text(text = "${stringResource(R.string.size_behavior_label)}: $sizeText")
                    Slider(
                        value = sizeBehavior,
                        onValueChange = { sizeBehavior = it },
                        onValueChangeFinished = { onSizeBehaviorChangeFinished(sizeBehavior) },
                        valueRange = BouncerPhysics.MIN_SIZE_BEHAVIOR..BouncerPhysics.MAX_SIZE_BEHAVIOR,
                        modifier = sliderModifier,
                    )
                    Text(text = stringResource(R.string.lifespan_label, lifespanBase.toInt()))
                    Slider(
                        value = lifespanBase,
                        onValueChange = { lifespanBase = it },
                        onValueChangeFinished = { onLifespanBaseChangeFinished(lifespanBase) },
                        valueRange = BouncerPhysics.MIN_LIFESPAN_SECONDS..BouncerPhysics.MAX_LIFESPAN_SECONDS,
                        modifier = sliderModifier,
                    )
                }

                SettingsSectionCard(
                    title = stringResource(R.string.settings_section_interaction),
                    summary = stringResource(R.string.settings_section_interaction_summary),
                    expanded = expandedSection == SettingsSection.INTERACTION,
                    onToggle = {
                        expandedSection = expandedSection.toggle(SettingsSection.INTERACTION)
                    },
                ) {
                    SettingsSwitchRow(
                        label = stringResource(R.string.solid_body_physics),
                        checked = physicsEnabled,
                        onCheckedChange = {
                            physicsEnabled = it
                            onPhysicsEnabledChange(it)
                        },
                    )
                    SettingsSwitchRow(
                        label = stringResource(R.string.destroy_on_touch),
                        checked = destroyOnTouch,
                        onCheckedChange = {
                            destroyOnTouch = it
                            onDestroyOnTouchChange(it)
                        },
                    )
                }

                SettingsSectionCard(
                    title = stringResource(R.string.settings_section_performance),
                    summary = stringResource(R.string.settings_section_performance_summary),
                    expanded = expandedSection == SettingsSection.PERFORMANCE,
                    onToggle = {
                        expandedSection = expandedSection.toggle(SettingsSection.PERFORMANCE)
                    },
                ) {
                    Text(
                        text = stringResource(
                            R.string.calibrated_device_cap,
                            initialState.deviceMaxBallCount,
                            initialState.detectedRefreshRateHz,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = stringResource(R.string.ball_count_label, ballCount.toInt()))
                    Slider(
                        value = ballCount,
                        onValueChange = { ballCount = it },
                        onValueChangeFinished = { onBallCountChangeFinished(ballCount) },
                        valueRange = BouncerPhysics.MIN_BALL_COUNT.toFloat()..initialState.deviceMaxBallCount.toFloat(),
                        modifier = sliderModifier,
                    )
                    Text(
                        text = stringResource(R.string.ball_count_calibration_note),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.high_ball_count_note, initialState.deviceMaxBallCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onReRunCalibration,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.rerun_calibration))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    @Composable
    private fun SettingsSectionCard(
        title: String,
        summary: String,
        expanded: Boolean,
        onToggle: () -> Unit,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggle)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = stringResource(
                            if (expanded) R.string.collapse_section else R.string.expand_section,
                        ),
                    )
                }
                if (expanded) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        content = content,
                    )
                }
            }
        }
    }

    @Composable
    private fun SettingsSwitchRow(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }

    @Preview(showBackground = true)
    @Composable
    private fun PreviewSettingsScreen() {
        BouncerScreenSaverTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                SettingsScreenContent(
                    initialState = SettingsScreenState(
                        ballCount = 36f,
                        ballSpeed = 4.5f,
                        selectedPalette = ColorPalette.NEON,
                        selectedBallStyle = BallStyle.AUTO,
                        brightness = BallAppearance.DEFAULT_BRIGHTNESS,
                        transparency = BallAppearance.DEFAULT_TRANSPARENCY,
                        physicsEnabled = true,
                        sizeBehavior = -0.5f,
                        lifespanBase = 15f,
                        destroyOnTouch = false,
                        deviceMaxBallCount = 64,
                        deviceMaxBallSpeed = 6f,
                        detectedRefreshRateHz = 60,
                    ),
                    onBack = {},
                    onDone = {},
                    onPhysicsEnabledChange = {},
                    onDestroyOnTouchChange = {},
                    onBallCountChangeFinished = {},
                    onReRunCalibration = {},
                    onBallSpeedChangeFinished = {},
                    onSizeBehaviorChangeFinished = {},
                    onLifespanBaseChangeFinished = {},
                    onPaletteSelected = {},
                    onBallStyleSelected = {},
                    onBrightnessChangeFinished = {},
                    onTransparencyChangeFinished = {},
                )
            }
        }
    }

    private data class SettingsScreenState(
        val ballCount: Float,
        val ballSpeed: Float,
        val selectedPalette: ColorPalette,
        val selectedBallStyle: BallStyle,
        val brightness: Float,
        val transparency: Float,
        val physicsEnabled: Boolean,
        val sizeBehavior: Float,
        val lifespanBase: Float,
        val destroyOnTouch: Boolean,
        val deviceMaxBallCount: Int,
        val deviceMaxBallSpeed: Float,
        val detectedRefreshRateHz: Int,
    )

    private enum class SettingsSection {
        APPEARANCE,
        MOTION,
        INTERACTION,
        PERFORMANCE,
    }

    private fun SettingsSection?.toggle(section: SettingsSection): SettingsSection? =
        if (this == section) null else section

}
