package io.toolbox.app

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationSafetyInstrumentationTest {
    @Test
    fun runtimeIsExactlyAndroid11Arm64() {
        assertEquals(30, Build.VERSION.SDK_INT)
        assertTrue(Build.SUPPORTED_ABIS.contains("arm64-v8a"))
    }

    @Test
    fun kernelStartsAndHealthSurfaceIsVisible() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(MainActivity.STATUS_VIEW_ID)).check(matches(isDisplayed()))
            onView(withId(MainActivity.STATUS_VIEW_ID)).check(matches(withText(containsString("RUNNING: ToolBox"))))
        }
    }

    @Test
    fun recreationRestoresStateWithoutDuplicateFailure() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(MainActivity.STATUS_VIEW_ID)).check(matches(withText(containsString("Generation 0"))))
            scenario.recreate()
            onView(withId(MainActivity.STATUS_VIEW_ID)).check(matches(withText(containsString("Generation 1"))))
            onView(withId(MainActivity.STATUS_VIEW_ID)).check(matches(withText(containsString("RUNNING: ToolBox"))))
        }
    }

    @Test
    fun repeatedLifecycleRecreationRemainsOperational() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            repeat(20) { scenario.recreate() }
            onView(withId(MainActivity.STATUS_VIEW_ID)).check(matches(withText(containsString("Generation 20"))))
            onView(withId(MainActivity.STATUS_VIEW_ID)).check(matches(withText(containsString("RUNNING: ToolBox"))))
        }
    }

    @Test
    fun unexpectedExternalPayloadDoesNotChangeBootstrapSemantics() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val launchIntent = targetContext.packageManager.getLaunchIntentForPackage(targetContext.packageName)
            ?: error("Launcher intent unavailable")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        launchIntent.putExtra("unexpected.external.payload", "../../invalid\u0000payload")
        targetContext.startActivity(launchIntent)
        instrumentation.waitForIdleSync()
        onView(withId(MainActivity.STATUS_VIEW_ID)).check(matches(withText(containsString("RUNNING: ToolBox"))))
    }

    @Test
    fun backgroundAndResumeKeepsKernelOperational() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(MainActivity.STATUS_VIEW_ID)).check(matches(withText(containsString("RUNNING: ToolBox"))))
            device.pressHome()
            device.waitForIdle()

            val launchIntent = targetContext.packageManager.getLaunchIntentForPackage(targetContext.packageName)
                ?: error("Launcher intent unavailable")
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            targetContext.startActivity(launchIntent)
            instrumentation.waitForIdleSync()

            onView(withId(MainActivity.STATUS_VIEW_ID)).check(matches(withText(containsString("RUNNING: ToolBox"))))
        }
    }
}
