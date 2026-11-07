package com.wangzan101.mika.location

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.core.general.LocationResult
import com.wangzan101.mika.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run on an emulator with location permissions initially revoked and a mock GPS position supplied. */
@RunWith(AndroidJUnit4::class)
class LocationPermissionFlowDeviceTest {
    @Test fun firstNearbyRequestShowsConsentThenAndroidPermissionAndReturnsGps() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as MikaApp
        val service = instrumentation.uiAutomation.serviceInfo
        service.flags = service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = service
        assertFalse("Revoke location permissions before running this first-use test", app.deviceLocation.hasPermission())
        ActivityScenario.launch(MainActivity::class.java).use {
            val result = async(Dispatchers.Default) { app.deviceLocation.current() }
            withTimeout(8_000) { app.deviceLocation.consent.filterNotNull().first() }
            android.util.Log.i("NemoLocationTest", "Waiting for app consent")
            withTimeout(10_000) {
                while (true) {
                    val node = find(instrumentation.uiAutomation.rootInActiveWindow) { it.text?.toString() == "Continue" }
                    if (node != null && click(node)) break
                    delay(100)
                }
            }
            android.util.Log.i("NemoLocationTest", "Waiting for Android permission")
            withTimeout(10_000) {
                while (true) {
                    val root = instrumentation.uiAutomation.rootInActiveWindow
                    val node = find(root) { it.viewIdResourceName?.endsWith(":id/permission_allow_foreground_only_button") == true || it.text?.toString()?.startsWith("While using") == true }
                    if (node != null) {
                        find(root) { it.text?.toString() == "Precise" }?.let(::click)
                        if (click(node)) break
                    }
                    delay(100)
                }
            }
            android.util.Log.i("NemoLocationTest", "Android grant pressed; waiting for location")
            val fix = withTimeout(25_000) { result.await() }
            assertTrue("Expected a location after granting permission, got $fix", fix is LocationResult.Fix)
            assertTrue(app.deviceLocation.hasPermission())
            assertNull(app.deviceLocation.consent.value)
            assertTrue((fix as LocationResult.Fix).usable())
        }
    }
    private fun find(root: AccessibilityNodeInfo?, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        if (matches(root)) return root
        for (i in 0 until root.childCount) find(root.getChild(i), matches)?.let { return it }
        return null
    }
    private fun click(start: AccessibilityNodeInfo): Boolean {
        var node: AccessibilityNodeInfo? = start
        while (node != null) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            node = node.parent
        }
        return false
    }
}
