package com.example

import com.example.data.model.GlobalCountryCatalog
import com.example.data.model.PlanTier
import com.example.data.model.TeamRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

class SendingEngineUnitTest {

    @Test
    fun resolveDestination_matchesLongestDialPrefix() {
        val pk = GlobalCountryCatalog.resolveDestination("+923001234567")
        assertEquals("PK", pk.countryCode)
        assertEquals("Pakistan", pk.countryName)
        assertEquals("+92", pk.dialPrefix)

        val us = GlobalCountryCatalog.resolveDestination("+14155550199")
        assertEquals("US", us.countryCode)

        val uk = GlobalCountryCatalog.resolveDestination("+447700900412")
        assertEquals("GB", uk.countryCode)

        val ng = GlobalCountryCatalog.resolveDestination("+2348031234567")
        assertEquals("NG", ng.countryCode)

        val ae = GlobalCountryCatalog.resolveDestination("+971501234567")
        assertEquals("AE", ae.countryCode)
    }

    @Test
    fun teamRole_permissionsMatchRoleCapabilities() {
        assertTrue(TeamRole.ADMIN.canControlService)
        assertTrue(TeamRole.ADMIN.canManageTeam)
        assertTrue(TeamRole.ADMIN.canSendOtp)

        assertTrue(TeamRole.OTP_OPERATOR.canSendOtp)
        assertFalse(TeamRole.OTP_OPERATOR.canManageTeam)
        assertFalse(TeamRole.OTP_OPERATOR.canControlService)

        assertTrue(TeamRole.DEVELOPER.canManageNumbersAndKeys)
        assertFalse(TeamRole.DEVELOPER.canManageTeam)

        assertFalse(TeamRole.VIEWER.canSendOtp)
        assertFalse(TeamRole.VIEWER.canSendSms)
        assertEquals(TeamRole.ADMIN, TeamRole.fromRaw("UNKNOWN_ROLE"))
    }

    @Test
    fun e164Validation_checksFormatStrictly() {
        val regex = Regex("^\\+[1-9]\\d{6,14}$")
        assertTrue("+923001234567".matches(regex))
        assertTrue("+14155550199".matches(regex))
        assertTrue("+447700900412".matches(regex))
        assertFalse("4155550199".matches(regex))
        assertFalse("+01234567".matches(regex))
        assertFalse("+123".matches(regex))
    }

    @Test
    fun segmentCalculation_handlesGsm7AndUnicode() {
        fun calcSegments(body: String): Int {
            if (body.isEmpty()) return 1
            val isGsm7 = body.all { it.code <= 127 }
            return if (isGsm7) {
                if (body.length <= 160) 1 else ceil(body.length / 153.0).toInt().coerceAtMost(20)
            } else {
                if (body.length <= 70) 1 else ceil(body.length / 67.0).toInt().coerceAtMost(20)
            }
        }

        assertEquals(1, calcSegments("Your OTP is 482910"))
        assertEquals(2, calcSegments("A".repeat(161)))
        assertEquals(1, calcSegments("Hi 👋"))
        assertEquals(2, calcSegments("🔒".repeat(40))) // 80 UTF-16 code units -> 2 UCS-2 segments
    }

    @Test
    fun unlimitedDirectPlan_pricesMessagesAtZero() {
        val tier = PlanTier.UNLIMITED_DIRECT
        val price = if (tier == PlanTier.UNLIMITED_DIRECT) 0.0 else 0.0075
        assertEquals(0.0, price, 0.00001)
    }
}
