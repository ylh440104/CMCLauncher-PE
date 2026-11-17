package com.movtery.cmclauncher.game.version.installed.utils

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun testCompareLegacyReleases() {
        assertTrue("1.8".isBiggerVer("1.7"))
        assertTrue("1.0".isBiggerOrEqualVer("1.0"))
        assertTrue("1.16".isLowerVer("1.17"))
    }

    @Test
    fun testCompareLegacySnapshots() {
        assertTrue("20w14b".isBiggerVer("someRelease"))
        assertTrue("20w14a".isLowerVer("someRelease"))
    }

    @Test
    fun testCompareNewReleases() {
        assertTrue("26.3".isBiggerOrEqualVer("26.2"))
        assertTrue("26.1".isLowerOrEqualVer("26.1"))
        assertFalse("26.1".isBiggerVer("26.2"))
    }

    @Test
    fun testCompareNewSnapshots() {
        assertTrue("25.4-snapshot-2".isBiggerVer("25.4"))
        assertTrue("25.4-snapshot-1".isLowerVer("25.4"))
    }

    @Test
    fun testNewVsLegacy() {
        assertTrue("26.1".isBiggerVer("1.21.11"))
        assertTrue("26.2-snapshot-1".isBiggerVer("1.21.11"))
        assertTrue("25.4-snapshot-1".isBiggerVer("23w40a"))
        assertFalse("20w14a".isBiggerVer("1.21.11"))
    }

    @Test
    fun testVersionEquality() {
        assertTrue("1.21.5".isBiggerOrEqualVer("1.21.5"))
        assertTrue("20w30a".isLowerOrEqualVer("1.21.5"))
    }

}
