package com.movtery.cmclauncher.sponsor

import org.junit.Assert.assertEquals
import org.junit.Test

class SponsorPlanEffectTest {

    @Test
    fun `known plan prefixes map to their effects`() {
        assertEquals(SponsorPlanEffect.LAPIS, SponsorPlanEffect.of("c1e7e62e"))
        assertEquals(SponsorPlanEffect.RED_CAT_HAT, SponsorPlanEffect.of("c3a32536"))
        assertEquals(SponsorPlanEffect.GOLDEN_TOKEN, SponsorPlanEffect.of("c4b309b8"))
    }

    @Test
    fun `only the first eight characters are needed`() {
        assertEquals(SponsorPlanEffect.LAPIS, SponsorPlanEffect.of("c1e7e62e-arbitrary-suffix"))
        assertEquals(SponsorPlanEffect.RED_CAT_HAT, SponsorPlanEffect.of("c3a32536-arbitrary-suffix"))
        assertEquals(SponsorPlanEffect.GOLDEN_TOKEN, SponsorPlanEffect.of("c4b309b8-arbitrary-suffix"))
    }

    @Test
    fun `known prefixes are case insensitive`() {
        assertEquals(SponsorPlanEffect.LAPIS, SponsorPlanEffect.of("C1E7E62E-suffix"))
        assertEquals(SponsorPlanEffect.RED_CAT_HAT, SponsorPlanEffect.of("C3A32536-suffix"))
        assertEquals(SponsorPlanEffect.GOLDEN_TOKEN, SponsorPlanEffect.of("C4B309B8-suffix"))
    }

    @Test
    fun `custom and unknown plans use emerald`() {
        assertEquals(SponsorPlanEffect.EMERALD, SponsorPlanEffect.of(""))
        assertEquals(SponsorPlanEffect.EMERALD, SponsorPlanEffect.of("unknown-plan"))
        assertEquals(SponsorPlanEffect.EMERALD, SponsorPlanEffect.of("c1e7e62"))
    }
}
