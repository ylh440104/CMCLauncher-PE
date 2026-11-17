package com.movtery.cmclauncher.utils.festival

import org.junit.Test
import java.time.LocalDate

class FestivalsTest {

    @Test
    fun testGetTodayFestivals() {
        val year = 2026
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)

        var currentDate = startDate

        while (currentDate <= endDate) {
            val list = getTodayFestivals(date = currentDate, true)
            if (list.isNotEmpty()) {
                println("month = ${currentDate.monthValue}, day = ${currentDate.dayOfMonth}, festivals = ${list.joinToString(",", "[", "]")}")
            }

            currentDate = currentDate.plusDays(1)
        }
    }
}
