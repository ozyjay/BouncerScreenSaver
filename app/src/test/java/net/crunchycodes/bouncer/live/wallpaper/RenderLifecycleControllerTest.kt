package net.crunchycodes.bouncer.live.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderLifecycleControllerTest {
    @Test
    fun visibleSurfaceReadyStateStartsRendering() {
        val controller = RenderLifecycleController()

        assertEquals(RenderAction.None, controller.onSurfaceChanged(true))
        val action = controller.onVisibilityChanged(true)

        assertTrue(action is RenderAction.Start)
        assertEquals(1, (action as RenderAction.Start).threadId)
    }

    @Test
    fun hiddenStateDoesNotRender() {
        val controller = startedController()

        val action = controller.onVisibilityChanged(false)

        assertEquals(RenderAction.Stop(1), action)
        assertFalse(controller.currentState().wallpaperVisible)
    }

    @Test
    fun surfaceDestroyedStopsRendering() {
        val controller = startedController()

        val action = controller.onSurfaceChanged(false)

        assertEquals(RenderAction.Stop(1), action)
        assertFalse(controller.currentState().surfaceReady)
    }

    @Test
    fun destroyedEngineCannotRestart() {
        val controller = startedController()
        controller.onDestroyed()
        controller.onThreadExited(1)

        val action = controller.onVisibilityChanged(true)

        assertEquals(RenderAction.None, action)
        assertTrue(controller.currentState().destroyed)
        assertNull(controller.currentState().currentThreadId)
    }

    @Test
    fun duplicateStartRequestsDoNotCreateMultipleThreads() {
        val controller = RenderLifecycleController()

        val first = controller.onVisibilityChanged(true)
        val second = controller.onSurfaceChanged(true)
        val third = controller.updateRenderingState()

        assertEquals(RenderAction.None, first)
        assertEquals(RenderAction.Start(1), second)
        assertEquals(RenderAction.None, third)
        assertEquals(1, controller.currentState().currentThreadId)
    }

    @Test
    fun visibleAgainWhileStoppingRestartsAfterExit() {
        val controller = startedController()
        controller.onVisibilityChanged(false)
        assertTrue(controller.currentState().currentThreadStopping)

        val resumedAction = controller.onVisibilityChanged(true)
        val exitResult = controller.onThreadExited(1)

        assertEquals(RenderAction.None, resumedAction)
        assertEquals(2, exitResult.restartThreadId)
        assertEquals(2, controller.currentState().currentThreadId)
    }

    @Test
    fun oldThreadExitCannotClearNewerThread() {
        val controller = startedController()
        controller.onVisibilityChanged(false)
        controller.onVisibilityChanged(true)
        controller.onThreadExited(1)

        val staleExit = controller.onThreadExited(1)

        assertTrue(staleExit.ignored)
        assertEquals(2, controller.currentState().currentThreadId)
    }

    @Test
    fun rapidVisibilityChangesSettleToFinalState() {
        val controller = startedController()
        controller.onVisibilityChanged(false)
        controller.onVisibilityChanged(true)
        controller.onVisibilityChanged(false)

        val exitResult = controller.onThreadExited(1)
        val restartAction = controller.onVisibilityChanged(true)

        assertNull(exitResult.restartThreadId)
        assertEquals(RenderAction.Start(2), restartAction)
        assertEquals(2, controller.currentState().currentThreadId)
    }

    private fun startedController(): RenderLifecycleController = RenderLifecycleController().apply {
        onSurfaceChanged(true)
        onVisibilityChanged(true)
    }
}
