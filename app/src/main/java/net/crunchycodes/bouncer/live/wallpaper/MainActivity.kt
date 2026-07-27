package net.crunchycodes.bouncer.live.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.crunchycodes.bouncer.live.wallpaper.ui.theme.BouncerScreenSaverTheme
import kotlin.math.*
import kotlin.random.Random

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
        
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Live simulation background
            BouncerSimulationBackground()

            // Semi-transparent overlay for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        startY = 300f
                    ))
            )

            Scaffold(
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "BOUNCER",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 8.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "High-Performance Live Wallpaper",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = {
                            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                putExtra(
                                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                    ComponentName(context, BouncerWallpaperService::class.java)
                                )
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            "SET WALLPAPER",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
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
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "CUSTOMIZE",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    @Composable
    fun BouncerSimulationBackground() {
        val balls = remember { mutableStateListOf<UiBall>() }
        val infiniteTransition = rememberInfiniteTransition(label = "sim")
        
        // This acts as our "frame clock"
        val frame by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(16, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "clock"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Initialize balls if empty
            if (balls.isEmpty()) {
                repeat(15) {
                    balls.add(createUiBall(width, height))
                }
            }

            // Physics Update (Simplified for UI)
            balls.forEach { ball ->
                ball.x += ball.dx
                ball.y += ball.dy

                if (ball.x < 0 || ball.x > width) ball.dx *= -1
                if (ball.y < 0 || ball.y > height) ball.dy *= -1
                
                // Draw with glow
                drawIntoCanvas { canvas ->
                    val paint = Paint().asFrameworkPaint().apply {
                        isAntiAlias = true
                        shader = android.graphics.RadialGradient(
                            ball.x, ball.y, ball.radius * 2f,
                            intArrayOf(ball.color.toArgb(), android.graphics.Color.TRANSPARENT),
                            null, android.graphics.Shader.TileMode.CLAMP
                        )
                    }
                    canvas.nativeCanvas.drawCircle(ball.x, ball.y, ball.radius * 2f, paint)
                }
            }
            
            // Trigger recomposition on frame change
            frame.let { }
        }
    }

    private fun createUiBall(width: Float, height: Float): UiBall {
        return UiBall(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height,
            dx = (Random.nextFloat() - 0.5f) * 10f,
            dy = (Random.nextFloat() - 0.5f) * 10f,
            radius = Random.nextFloat() * 50f + 20f,
            color = Color(Random.nextFloat(), Random.nextFloat(), Random.nextFloat(), 0.5f)
        )
    }

    data class UiBall(
        var x: Float,
        var y: Float,
        var dx: Float,
        var dy: Float,
        val radius: Float,
        val color: Color
    )
}
