// Modified for Nemo-Engine by MAGI ABHISHEKAR, 2026-11-07.
package com.wangzan101.mika.core.general

import com.wangzan101.mika.core.prompt.Prompts
import com.wangzan101.mika.core.tool.*
import kotlinx.serialization.json.put

/** On-demand location, supplied by the host only for an attended user task. */
fun interface DeviceLocation {
    suspend fun current(): LocationResult
}

sealed interface LocationResult {
    data class Fix(val latitude: Double, val longitude: Double, val accuracyMeters: Double,
        val ageMillis: Long, val capturedAtMillis: Long, val approximate: Boolean) : LocationResult {
        fun usable() = latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() &&
            longitude in -180.0..180.0 && accuracyMeters.isFinite() && accuracyMeters > 0 && ageMillis in 0..MAX_AGE_MS
    }
    data class Unavailable(val code: String, val message: String) : LocationResult
    companion object { const val MAX_AGE_MS = 120_000L }
}

internal object GetCurrentLocation : AgentTool {
    override val verb = Verb.GetCurrentLocation
    override val risk = Risk.Read
    override val offScreen = true
    override val label = "Get current location"
    override val def = ToolDef("current_location", Prompts.tool("current_location"), emptyMap(), emptyList())
    override fun available(ctx: ToolContext) = ctx.config.deviceLocation != null &&
        ctx.config.runSpec?.provenance?.origin == com.wangzan101.mika.core.run.RunOrigin.Ui &&
        ctx.config.runSpec?.provenance?.attendance != com.wangzan101.mika.core.run.Attendance.Unattended
    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        return when (val result = ctx.config.deviceLocation?.current()
            ?: LocationResult.Unavailable("location_unavailable", "Device location is unavailable for this task.")) {
            is LocationResult.Unavailable -> fail(result.code, result.message)
            is LocationResult.Fix -> if (!result.usable()) fail("location_unavailable", "No recent valid location fix is available. Try again outdoors with Location enabled.") else ok {
                put("latitude", result.latitude); put("longitude", result.longitude)
                put("accuracyMeters", result.accuracyMeters); put("ageMillis", result.ageMillis)
                put("capturedAtMillis", result.capturedAtMillis); put("approximate", result.approximate)
            }
        }
    }
}
