package com.cmc.launcher.game.account

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import java.io.File
import java.util.Locale
import java.util.TimeZone

object RegionGate {

    data class Verdict(
        val score: Int,
        val mainland: Boolean,
        val signals: List<Pair<String, Int>>
    )

    private val mainlandTimeZones = setOf(
        "Asia/Shanghai",
        "Asia/Chongqing",
        "Asia/Harbin",
        "Asia/Urumqi",
        "Asia/Kashgar",
        "PRC"
    )

    private val domesticBrands = setOf(
        "huawei", "honor", "xiaomi", "redmi", "poco", "oppo", "vivo", "oneplus",
        "realme", "meizu", "lenovo", "zuk", "zte", "nubia", "iqoo", "coolpad",
        "gionee", "smartisan", "blackshark", "tecno", "infinix", "transsion",
        "hisense", "tcl", "doogee", "ulefone", "umidigi", "blackview", "oukitel"
    )

    fun evaluate(context: Context): Verdict {
        val signals = ArrayList<Pair<String, Int>>(8)

        val tz = TimeZone.getDefault().id
        signals.add("timezone" to if (tz in mainlandTimeZones) 30 else 0)

        val locale = Locale.getDefault()
        val lang = locale.language.lowercase()
        val country = locale.country.uppercase()
        val tag = locale.toString()
        val localeScore = when {
            lang == "zh" && country == "CN" -> 22
            tag.startsWith("zh_Hans") || tag.startsWith("zh-hans") -> 22
            lang == "zh" -> 14
            else -> 0
        }
        signals.add("locale" to localeScore)

        signals.add("fonts" to if (hasChineseFonts()) 14 else 0)

        signals.add("carrier" to if (isMainlandCarrier(context)) 10 else 0)

        val brand = Build.MANUFACTURER.lowercase()
        val brand2 = Build.BRAND.lowercase()
        signals.add("brand" to if (brand in domesticBrands || brand2 in domesticBrands) 6 else 0)

        signals.add("region" to if (country == "CN") 6 else 0)

        val total = signals.sumOf { it.second }
        return Verdict(total, total >= 45, signals)
    }

    fun hasMicrosoftAccount(): Boolean =
        AccountsManager.accountsFlow.value.any { it.accountType == AccountType.MICROSOFT.tag }

    fun canUseOffline(context: Context): Boolean =
        evaluate(context).mainland || hasMicrosoftAccount()

    private fun hasChineseFonts(): Boolean = try {
        val dir = File("/system/fonts")
        val names = dir.list() ?: emptyArray()
        names.any { name ->
            val n = name.lowercase()
            n.contains("cjk") || n.contains("droidsansfallback") || n.contains("notosanssc") ||
                n.contains("notosanstc") || n.contains("chinese") || n.contains("han")
        }
    } catch (_: Throwable) {
        false
    }

    private fun isMainlandCarrier(context: Context): Boolean = try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val op = tm?.simOperator ?: ""
        op.startsWith("460")
    } catch (_: Throwable) {
        false
    }
}
