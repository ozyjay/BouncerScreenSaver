package net.crunchycodes.bouncer.live.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import net.crunchycodes.bouncer.live.wallpaper.ui.theme.BouncerScreenSaverTheme
import kotlin.random.Random
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BouncerScreenSaverTheme {
                DashboardScreen()
            }
        }
    }

    @Composable
    fun DashboardScreen() {
        val context = LocalContext.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            BouncerSimulationBackground()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 300f,
                        ),
                    ),
            )

            Scaffold(
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxSize(),
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        bitmap = ImageBitmap.imageResource(R.drawable.ic_launcher_art),
                        contentDescription = null,
                        modifier = Modifier.alpha(0.7f)
                    )

                    Text(
                        text = stringResource(R.string.dashboard_title),
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 8.sp,
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = stringResource(R.string.dashboard_subtitle),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = {
                            val intent =
                                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                    putExtra(
                                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                        ComponentName(context, BouncerWallpaperService::class.java),
                                    )
                                }
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.set_wallpaper),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                            .copy(width = 2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.open_settings),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.customize),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    @Preview
    @Composable
    fun PreviewDashboard() {
        DashboardScreen()
    }

    @Composable
    fun BouncerSimulationBackground() {
        val lifecycleOwner = LocalLifecycleOwner.current
        val balls = remember { mutableStateListOf<LauncherBall>() }
        val glowBitmap = remember { createGlowBitmap() }
        val glowRect = remember { RectF() }
        val glowPaint = remember { AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG) }
        val colorFilters = remember { mutableMapOf<Int, PorterDuffColorFilter>() }
        var isRunning by remember {
            mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        var canvasSize by remember { mutableStateOf(Size.Zero) }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                isRunning = when (event) {
                    Lifecycle.Event.ON_START -> true
                    Lifecycle.Event.ON_STOP -> false
                    else -> isRunning
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) },
        ) {
            val width = size.width
            val height = size.height

            if (width > 0f && height > 0f && balls.isEmpty()) {
                repeat(LauncherSimulation.BALL_COUNT) {
                    balls += createUiBall(width, height)
                }
            }

            drawIntoCanvas { canvas ->
                for (ball in balls) {
                    glowPaint.colorFilter = colorFilters.getOrPut(ball.color.toArgb()) {
                        PorterDuffColorFilter(ball.color.toArgb(), PorterDuff.Mode.SRC_IN)
                    }
                    glowPaint.alpha = 160
                    glowRect.set(
                        ball.x - ball.radius * 1.6f,
                        ball.y - ball.radius * 1.6f,
                        ball.x + ball.radius * 1.6f,
                        ball.y + ball.radius * 1.6f,
                    )
                    canvas.nativeCanvas.drawBitmap(glowBitmap, null, glowRect, glowPaint)
                }
            }
        }

        LaunchedEffect(isRunning, canvasSize) {
            if (!isRunning || canvasSize == Size.Zero) return@LaunchedEffect

            var previousFrameNanos = 0L
            while (isActive && isRunning) {
                withFrameNanos { frameTimeNanos ->
                    if (previousFrameNanos == 0L) {
                        previousFrameNanos = frameTimeNanos
                    }
                    val deltaSeconds = ((frameTimeNanos - previousFrameNanos) / 1_000_000_000f)
                        .coerceIn(0f, 0.05f)
                    previousFrameNanos = frameTimeNanos
                    LauncherSimulation.update(
                        balls = balls,
                        width = canvasSize.width,
                        height = canvasSize.height,
                        deltaSeconds = deltaSeconds,
                    )
                }
            }
        }
    }

    private fun createGlowBitmap(): Bitmap {
        val size = 200
        val bitmap = createBitmap(size, size)
        val canvas = AndroidCanvas(bitmap)
        val paint = AndroidPaint().apply {
            isAntiAlias = true
            shader = RadialGradient(
                size / 2f,
                size / 2f,
                size / 2f,
                intArrayOf(AndroidColor.WHITE, AndroidColor.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return bitmap
    }

    private fun createUiBall(width: Float, height: Float): LauncherBall = LauncherBall(
        x = Random.nextFloat() * width,
        y = Random.nextFloat() * height,
        dx = (Random.nextFloat() - 0.5f) * 180f,
        dy = (Random.nextFloat() - 0.5f) * 180f,
        radius = Random.nextFloat() * 50f + 20f,
        color = Color(Random.nextFloat(), Random.nextFloat(), Random.nextFloat(), 0.5f),
    )
}