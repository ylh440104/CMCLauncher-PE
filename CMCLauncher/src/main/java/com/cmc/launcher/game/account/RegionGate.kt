package com.cmc.launcher.game.account

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.util.Locale
import java.util.TimeZone

object RegionGate {

    private const val W_TIMEZONE = 30
    private const val W_LANGUAGE = 22
    private const val W_CJK_FONT = 14
    private const val W_VENDOR_FONT = 12
    private const val W_BROWSER = 10
    private const val W_BRAND = 6
    private const val W_INTL = 6

    private const val THRESHOLD = 50

    data class Signal(val name: String, val score: Int, val max: Int, val hit: Boolean)

    data class Verdict(
        val score: Int,
        val mainland: Boolean,
        val signals: List<Signal>
    )

    private val mainlandTimeZones = setOf(
        "Asia/Shanghai",
        "Asia/Chongqing",
        "Asia/Harbin",
        "Asia/Urumqi",
        "Asia/Kashgar",
        "PRC",
        "Asia/Beijing"
    )

    private val vendorFontTokens = listOf(
        "sourcehansans", "sourcehanserif", "misans", "miaowu", "opposans",
        "vivofonts", "honorsans", "harmonysans", "alipuhui", "droidsansfallback",
        "hihonor", "colorossans", "flyme", "hwxw", "hmos"
    )

    private val chineseFontTokens = listOf(
        "cjk", "droidsansfallback", "notosanssc", "notosanstc", "chinese", "han"
    )

    private val domesticBrowsers = listOf(
        "com.tencent.mtt",
        "com.UCMobile",
        "com.ucmobile",
        "com.quark.browser",
        "com.heytap.browser",
        "com.vivo.browser",
        "com.huawei.browser",
        "com.miui.browser",
        "com.baidu.browser",
        "com.qihoo.browser",
        "com.oupeng.browser",
        "com.tencent.qb"
    )

    private val domesticBrands = setOf(
        "huawei", "honor", "xiaomi", "redmi", "poco", "oppo", "vivo", "oneplus",
        "realme", "meizu", "lenovo", "zuk", "zte", "nubia", "iqoo", "coolpad",
        "gionee", "smartisan", "blackshark", "tecno", "infinix", "transsion",
        "hisense", "tcl", "doogee", "ulefone", "umidigi", "blackview", "oukitel"
    )

    fun evaluate(context: Context): Verdict {
        val signals = ArrayList<Signal>(7)

        val tzHit = TimeZone.getDefault().id in mainlandTimeZones
        signals.add(Signal("系统时区", if (tzHit) W_TIMEZONE else 0, W_TIMEZONE, tzHit))

        val locale = Locale.getDefault()
        val lang = locale.language.lowercase()
        val country = locale.country.uppercase()
        val tag = locale.toString()
        val langHit = lang == "zh" && (country == "CN" ||
            tag.startsWith("zh_Hans") || tag.startsWith("zh-hans"))
        signals.add(Signal("界面语言", if (langHit) W_LANGUAGE else 0, W_LANGUAGE, langHit))

        val cjkHit = hasChineseFonts()
        signals.add(Signal("中文字体", if (cjkHit) W_CJK_FONT else 0, W_CJK_FONT, cjkHit))

        val vendorHit = hasVendorFonts()
        signals.add(Signal("国产厂商字体", if (vendorHit) W_VENDOR_FONT else 0, W_VENDOR_FONT, vendorHit))

        val browserHit = hasDomesticBrowser(context)
        signals.add(Signal("国产浏览器", if (browserHit) W_BROWSER else 0, W_BROWSER, browserHit))

        val brand = Build.MANUFACTURER.lowercase()
        val brand2 = Build.BRAND.lowercase()
        val brandHit = brand in domesticBrands || brand2 in domesticBrands
        signals.add(Signal("国产设备品牌", if (brandHit) W_BRAND else 0, W_BRAND, brandHit))

        val intlHit = country == "CN"
        signals.add(Signal("区域设置", if (intlHit) W_INTL else 0, W_INTL, intlHit))

        val total = signals.sumOf { it.score }
        return Verdict(total, tzHit && total >= THRESHOLD, signals)
    }

    fun hasMicrosoftAccount(): Boolean =
        AccountsManager.accountsFlow.value.any { it.accountType == AccountType.MICROSOFT.tag }

    fun requiresMicrosoftFirst(context: Context): Boolean {
        val inMainland = evaluate(context).mainland
        if (inMainland) return false
        return !hasMicrosoftAccount()
    }

    fun canUseOffline(context: Context): Boolean = !requiresMicrosoftFirst(context)

    private fun fontNames(): Array<String> = try {
        File("/system/fonts").list() ?: emptyArray()
    } catch (_: Throwable) {
        emptyArray()
    }

    private fun hasChineseFonts(): Boolean {
        val names = fontNames()
        if (names.isEmpty()) return false
        return names.any { name ->
            val n = name.lowercase()
            chineseFontTokens.any { n.contains(it) }
        }
    }

    private fun hasVendorFonts(): Boolean {
        val names = fontNames()
        if (names.isEmpty()) return false
        return names.any { name ->
            val n = name.lowercase()
            vendorFontTokens.any { n.contains(it) }
        }
    }

    private fun hasDomesticBrowser(context: Context): Boolean {
        val pm = context.packageManager
        return domesticBrowsers.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (_: Throwable) {
                false
            }
        }
    }
}
