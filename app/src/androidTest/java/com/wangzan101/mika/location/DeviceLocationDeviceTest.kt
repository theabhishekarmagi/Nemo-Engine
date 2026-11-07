package com.wangzan101.mika.location

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
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

@RunWith(AndroidJUnit4::class)
class DeviceLocationDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MikaApp

    @Test fun backgroundRequestDoesNotAccessLocationOrAskForPermission() = runBlocking {
        val access = DeviceLocationAccess(app)
        val result = access.current() as LocationResult.Unavailable
        assertEquals("location_foreground_required", result.code)
        assertNull(access.consent.value)
    }

    @Test fun firstRequestCanBeDeclinedWithoutInventingCoordinates() = runBlocking {
        val denied = object : ContextWrapper(app) {
            override fun checkSelfPermission(permission: String) = PackageManager.PERMISSION_DENIED
        }
        val access = DeviceLocationAccess(denied)
        access.foreground(true)
        val pending = async { access.current() }
        withTimeout(5_000) { access.consent.filterNotNull().first() }
        access.completeConsent(false)
        assertEquals("location_permission_denied", (pending.await() as LocationResult.Unavailable).code)
        assertNull(access.consent.value)
    }

    @Suppress("DEPRECATION")
    @Test fun foregroundTaskReadsARecentAndroidGpsFix() = runBlocking {
        val ua = instrumentation.uiAutomation
        ua.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        ua.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        ua.executeShellCommand("appops set ${app.packageName} android:mock_location allow").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        val manager = app.getSystemService(LocationManager::class.java)
        manager.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false, true, true, true,
            Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
        try {
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            val location = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 12.9716; longitude = 77.5946; accuracy = 25f
                time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
            ActivityScenario.launch(MainActivity::class.java).use {
                delay(300)
                val result = withTimeout(25_000) { app.deviceLocation.current() }
                assertTrue("Expected GPS fix, got $result", result is LocationResult.Fix)
                val fix = result as LocationResult.Fix
                assertEquals(location.latitude, fix.latitude, 0.000001)
                assertEquals(location.longitude, fix.longitude, 0.000001)
                assertFalse(fix.approximate)
                assertTrue(fix.usable())
            }
        } finally {
            manager.removeTestProvider(LocationManager.GPS_PROVIDER)
            ua.executeShellCommand("appops set ${app.packageName} android:mock_location default").close()
        }
    }
}
