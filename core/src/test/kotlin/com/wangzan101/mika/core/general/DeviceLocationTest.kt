package com.wangzan101.mika.core.general

import com.wangzan101.mika.core.assembly.toolDispatcher
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.tool.ActionPass
import com.wangzan101.mika.core.tool.FakeScreen
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DeviceLocationTest {
    private val good = LocationResult.Fix(12.97, 77.59, 100.0, 500, 1000, true)
    private fun tools(value: LocationResult) = toolDispatcher(FakeScreen(), deviceLocation = DeviceLocation { value })
    private fun call(value: LocationResult) = runBlocking {
        tools(value).execute(Part.ToolCall("location", "current_location", "{}"), ActionPass()).content
    }
    @Test fun unavailableWithoutAnExplicitHostPort() {
        assertFalse(toolDispatcher(FakeScreen()).definitions().any { it.name == "current_location" })
        assertTrue(tools(good).definitions().any { it.name == "current_location" })
    }
    @Test fun neverOfferedToRemoteOrUnattendedRuns() {
        val origins = listOf(com.wangzan101.mika.core.run.RunOrigin.Mcp, com.wangzan101.mika.core.run.RunOrigin.Trigger, com.wangzan101.mika.core.run.RunOrigin.Child)
        for (origin in origins) {
            val spec = com.wangzan101.mika.core.run.RunSpec(com.wangzan101.mika.core.run.RunId("x"), "near me", "x",
                provenance = com.wangzan101.mika.core.run.RunProvenance(origin = origin))
            assertFalse(toolDispatcher(FakeScreen(), web = com.wangzan101.mika.core.cap.web.FakeWebPort(), runSpec = spec, deviceLocation = DeviceLocation { good }).definitions().any { it.name == "current_location" })
        }
        val unattended = com.wangzan101.mika.core.run.RunSpec(com.wangzan101.mika.core.run.RunId("u"), "near me", "thread",
            provenance = com.wangzan101.mika.core.run.RunProvenance(attendance = com.wangzan101.mika.core.run.Attendance.Unattended))
        assertFalse(toolDispatcher(FakeScreen(), runSpec = unattended, deviceLocation = DeviceLocation { good }).definitions().any { it.name == "current_location" })
    }
    @Test fun returnsApproximateCoordinatesWithoutOpeningPhoneApps() {
        val output = call(good)
        assertEquals(true, output["ok"]?.jsonPrimitive?.boolean)
        assertEquals(12.97, output["latitude"]!!.jsonPrimitive.double, 0.0)
        assertEquals(true, output["approximate"]!!.jsonPrimitive.boolean)
        assertEquals(100.0, output["accuracyMeters"]!!.jsonPrimitive.double, 0.0)
    }
    @Test fun denialAndDisabledLocationRemainExplicitFailures() {
        for (code in listOf("location_permission_denied", "location_disabled", "location_foreground_required")) {
            val output = call(LocationResult.Unavailable(code, "Help the user enable it."))
            assertEquals(false, output["ok"]!!.jsonPrimitive.boolean)
            assertEquals(code, output["error"]!!.jsonPrimitive.content)
            assertNull(output["latitude"])
        }
    }
    @Test fun neverUsesStaleOrInvalidCoordinates() {
        for (bad in listOf(good.copy(ageMillis = 120_001), good.copy(latitude = Double.NaN),
            good.copy(longitude = 181.0), good.copy(accuracyMeters = -1.0), good.copy(ageMillis = -1))) {
            val output = call(bad)
            assertEquals(false, output["ok"]!!.jsonPrimitive.boolean)
            assertNull(output["latitude"])
        }
    }
}
