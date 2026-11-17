package com.cmc.launcher.game.versioninfo

import com.cmc.launcher.R
import org.jackhuang.hmcl.util.versioning.GameVersionNumber

val popularVersions = listOf(
    "26.2", "26.1",
    "1.21.11", "1.21.4", "1.21.1",
    "1.20.4", "1.20.1",
    "1.19.4", "1.19.2",
    "1.18.2",
    "1.16.5",
    "1.12.2",
    "1.8.9",
    "1.7.10",
)

enum class AprilFoolsType(
    val summary: Int? = null,
    val urlSuffix: String? = null
) {

    HerdCraft(R.string.version_summary_fools_herd_craft),

    CraftMine(R.string.version_summary_fools_creaft_mine),

    Potato(R.string.version_summary_fools_potato),

    AOrB(R.string.version_summary_fools_a_or_b),

    OneBlockAtATime(R.string.version_summary_fools_one_block_at_time),

    Infinite(R.string.version_summary_fools_infinite),

    Minecraft3DShareware(R.string.version_summary_fools_3d_shareware),

    TrendyUpdate(R.string.version_summary_fools_trendy_update),

    TheLoveAndHugsUpdate(R.string.version_summary_fools_the_love_and_hugs_update),

    `2_0`(R.string.version_summary_fools_2_0, "2.0")
}

data class AprilFoolsVersion(
    val version: String,
    val type: AprilFoolsType
)

val allAprilFools = listOf(
    AprilFoolsVersion("26w14a", AprilFoolsType.HerdCraft),
    AprilFoolsVersion("25w14craftmine", AprilFoolsType.CraftMine),
    AprilFoolsVersion("24w14potato", AprilFoolsType.Potato),
    AprilFoolsVersion("23w13a_or_b", AprilFoolsType.AOrB),
    AprilFoolsVersion("22w13oneblockatatime", AprilFoolsType.OneBlockAtATime),
    AprilFoolsVersion("20w14infinite", AprilFoolsType.Infinite),
    AprilFoolsVersion("20w14∞", AprilFoolsType.Infinite),
    AprilFoolsVersion("3D Shareware v1.34", AprilFoolsType.Minecraft3DShareware),
    AprilFoolsVersion("1.RV-Pre1", AprilFoolsType.TrendyUpdate),
    AprilFoolsVersion("15w14a", AprilFoolsType.TheLoveAndHugsUpdate),
    AprilFoolsVersion("2.0_blue", AprilFoolsType.`2_0`),
    AprilFoolsVersion("2.0_red", AprilFoolsType.`2_0`),
    AprilFoolsVersion("2.0_purple", AprilFoolsType.`2_0`),
)

fun filterRelease(versionString: String): Boolean {
    return GameVersionNumber.asGameVersion(versionString).isRelease
}
