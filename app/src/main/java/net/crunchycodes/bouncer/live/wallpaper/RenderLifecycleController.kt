package net.crunchycodes.bouncer.live.wallpaper

internal class RenderLifecycleController {
    private var wallpaperVisible = false
    private var surfaceReady = false
    private var destroyed = false
    private var currentThreadId: Int? = null
    private var currentThreadStopping = false
    private var nextThreadId = 1

    fun onVisibilityChanged(visible: Boolean): RenderAction {
        wallpaperVisible = visible
        return updateRenderingState()
    }

    fun onSurfaceChanged(ready: Boolean): RenderAction {
        surfaceReady = ready
        return updateRenderingState()
    }

    fun onDestroyed(): RenderAction {
        destroyed = true
        return updateRenderingState()
    }

    fun updateRenderingState(): RenderAction {
        if (shouldRender()) {
            if (currentThreadId == null) {
                val threadId = nextThreadId++
                currentThreadId = threadId
                currentThreadStopping = false
                return RenderAction.Start(threadId)
            }
            return RenderAction.None
        }

        val threadId = currentThreadId
        if (threadId != null && !currentThreadStopping) {
            currentThreadStopping = true
            return RenderAction.Stop(threadId)
        }
        return RenderAction.None
    }

    fun onThreadExited(threadId: Int): ThreadExitResult {
        if (currentThreadId != threadId) {
            return ThreadExitResult(ignored = true)
        }

        currentThreadId = null
        currentThreadStopping = false

        if (!shouldRender()) {
            return ThreadExitResult(cleared = true)
        }

        val replacementThreadId = nextThreadId++
        currentThreadId = replacementThreadId
        return ThreadExitResult(cleared = true, restartThreadId = replacementThreadId)
    }

    fun currentState(): RenderLifecycleSnapshot = RenderLifecycleSnapshot(
        wallpaperVisible = wallpaperVisible,
        surfaceReady = surfaceReady,
        destroyed = destroyed,
        currentThreadId = currentThreadId,
        currentThreadStopping = currentThreadStopping,
    )

    private fun shouldRender(): Boolean = wallpaperVisible && surfaceReady && !destroyed
}

internal sealed interface RenderAction {
    data object None : RenderAction
    data class Start(val threadId: Int) : RenderAction
    data class Stop(val threadId: Int) : RenderAction
}

internal data class ThreadExitResult(
    val ignored: Boolean = false,
    val cleared: Boolean = false,
    val restartThreadId: Int? = null,
)

internal data class RenderLifecycleSnapshot(
    val wallpaperVisible: Boolean,
    val surfaceReady: Boolean,
    val destroyed: Boolean,
    val currentThreadId: Int?,
    val currentThreadStopping: Boolean,
)
