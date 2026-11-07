// Modified for Nemo-Engine by MAGI ABHISHEKAR, 2026-11-07.
package com.wangzan101.mika.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import com.wangzan101.mika.core.general.DeviceLocation
import com.wangzan101.mika.core.general.LocationResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** No background permission, periodic updates, disk cache, or website permission grants. */
class DeviceLocationAccess(private val app: Context) : DeviceLocation {
    data class Consent(val id: Long, val awaitingSystem: Boolean = false)
    private val _consent = MutableStateFlow<Consent?>(null)
    val consent = _consent.asStateFlow()
    private val appName get() = app.getString(com.wangzan101.mika.R.string.app_name)
    private val mutex = Mutex()
    private var answer: CompletableDeferred<Boolean>? = null
    private var nextId = 0L
    @Volatile private var visible = false

    fun foreground(value: Boolean, changingConfiguration: Boolean = false) {
        visible = value
        if (!value && !changingConfiguration && _consent.value?.awaitingSystem != true) completeConsent(false)
    }

    fun hasPermission(): Boolean = granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun granted(permission: String) = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun awaitingSystem() { _consent.value = _consent.value?.copy(awaitingSystem = true) }
    fun completeConsent(allowed: Boolean) {
        answer?.complete(allowed)
        _consent.value = null
    }

    override suspend fun current(): LocationResult = mutex.withLock {
        if (!visible) return@withLock problem("location_foreground_required", "Open $appName, keep it visible, and retry this nearby search to read your location.")
        val manager = app.getSystemService(LocationManager::class.java)
            ?: return@withLock problem("location_unavailable", "This device has no location service.")
        if (!manager.isLocationEnabled) return@withLock problem("location_disabled", "Turn on Location in Android settings, return to $appName, and retry.")
        if (!hasPermission()) {
            val request = CompletableDeferred<Boolean>()
            withContext(Dispatchers.Main.immediate) {
                answer = request
                _consent.value = Consent(++nextId)
            }
            val allowed = try { withTimeoutOrNull(60_000) { request.await() } == true }
                finally { withContext(NonCancellable + Dispatchers.Main.immediate) { if (answer === request) { answer = null; _consent.value = null } } }
            if (!allowed || !hasPermission()) return@withLock problem("location_permission_denied", "Location permission was not granted. Allow Location for $appName in Android App info > Permissions, or provide an area if you prefer. Do not keep prompting.")
        }
        if (!visible) return@withLock problem("location_foreground_required", "Return to $appName and retry to read your location.")
        try {
            val result = read(manager)
            if (visible) result else problem("location_foreground_required", "Return to $appName and retry to read your location.")
        }
        catch (e: CancellationException) { throw e }
        catch (_: SecurityException) { problem("location_permission_denied", "Location permission was revoked. Allow it for $appName and retry.") }
        catch (_: Exception) { problem("location_unavailable", "Could not get a recent location. Keep Location enabled and try again with a clear signal.") }
    }

    @SuppressLint("MissingPermission")
    private suspend fun read(manager: LocationManager): LocationResult {
        val precise = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { (precise || it != LocationManager.GPS_PROVIDER) && manager.isProviderEnabled(it) }
        val recent = providers.mapNotNull { manager.getLastKnownLocation(it) }.map { fix(it, !precise) }
            .filter { it.usable() }.minByOrNull { it.accuracyMeters }
        if (recent != null) return recent
        for (provider in providers) {
            if (!visible) break
            val value = withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(provider, signal, app.mainExecutor) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                }
            }
            if (value != null) fix(value, !precise).takeIf { it.usable() }?.let { return it }
        }
        return problem("location_unavailable", "No recent location fix is available. Keep Location enabled, try again outdoors, or choose an area manually.")
    }

    private fun fix(location: Location, approximate: Boolean) = LocationResult.Fix(
        location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy.toDouble() else Double.NaN,
        ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000),
        location.time, approximate,
    )
    private fun problem(code: String, message: String) = LocationResult.Unavailable(code, message)
}
