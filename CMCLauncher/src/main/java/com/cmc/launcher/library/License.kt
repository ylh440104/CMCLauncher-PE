package com.cmc.launcher.library

import com.cmc.launcher.R

data class License(
    val name: String,
    val raw: Int
)

val LICENSE_APACHE_2 = License("Apache License 2.0", R.raw.apache_license_2)
