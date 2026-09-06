package vn.ai.lich.tv

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

data class LunarDate(
    val day: Int,
    val month: Int,
    val year: Int,
    val leap: Boolean,
    val julianDay: Int
)

object VietnameseLunar {
    private const val TZ = 7.0

    private fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        }
        return jd
    }

    private fun newMoon(k: Int): Double {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = PI / 180.0
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)

        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3

        var c1 = (0.1734 - 0.000393 * t) * sin(m * dr) + 0.0021 * sin(2 * m * dr)
        c1 -= 0.4068 * sin(mpr * dr) + 0.0161 * sin(2 * mpr * dr)
        c1 -= 0.0004 * sin(3 * mpr * dr)
        c1 += 0.0104 * sin(2 * f * dr) - 0.0051 * sin((m + mpr) * dr)
        c1 -= 0.0074 * sin((m - mpr) * dr) + 0.0004 * sin((2 * f + m) * dr)
        c1 -= 0.0004 * sin((2 * f - m) * dr) - 0.0006 * sin((2 * f + mpr) * dr)
        c1 += 0.0010 * sin((2 * f - mpr) * dr) + 0.0005 * sin((2 * mpr + m) * dr)

        val deltaT = if (t < -11) {
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        } else {
            -0.000278 + 0.000265 * t + 0.000262 * t2
        }
        return jd1 + c1 - deltaT
    }

    private fun sunLongitude(jdn: Double): Double {
        val t = (jdn - 2451545.0) / 36525.0
        val t2 = t * t
        val dr = PI / 180.0
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= PI * 2 * floor(l / (PI * 2))
        return l
    }

    private fun getNewMoonDay(k: Int): Int =
        floor(newMoon(k) + 0.5 + TZ / 24.0).toInt()

    private fun getSunLongitude(dayNumber: Int): Int =
        floor(sunLongitude(dayNumber - 0.5 - TZ / 24.0) / PI * 6).toInt()

    private fun getLunarMonth11(yy: Int): Int {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = floor(off / 29.530588853).toInt()
        var nm = getNewMoonDay(k)
        val sunLong = getSunLongitude(nm)
        if (sunLong >= 9) nm = getNewMoonDay(k - 1)
        return nm
    }

    private fun getLeapMonthOffset(a11: Int): Int {
        val k = floor(0.5 + (a11 - 2415021.076998695) / 29.530588853).toInt()
        var last = 0
        var i = 1
        var arc = getSunLongitude(getNewMoonDay(k + i))
        do {
            last = arc
            i++
            arc = getSunLongitude(getNewMoonDay(k + i))
        } while (arc != last && i < 14)
        return i - 1
    }

    fun fromSolar(date: LocalDate): LunarDate {
        val dd = date.dayOfMonth
        val mm = date.monthValue
        val yy = date.year
        val dayNumber = jdFromDate(dd, mm, yy)
        val k = floor((dayNumber - 2415021.076998695) / 29.530588853).toInt()
        var monthStart = getNewMoonDay(k + 1)
        if (monthStart > dayNumber) monthStart = getNewMoonDay(k)

        var a11 = getLunarMonth11(yy)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = getLunarMonth11(yy - 1)
        } else {
            lunarYear = yy + 1
            b11 = getLunarMonth11(yy + 1)
        }

        val lunarDay = dayNumber - monthStart + 1
        val diff = floor((monthStart - a11) / 29.0).toInt()
        var lunarLeap = false
        var lunarMonth = diff + 11

        if (b11 - a11 > 365) {
            val leapDiff = getLeapMonthOffset(a11)
            if (diff >= leapDiff) {
                lunarMonth = diff + 10
                if (diff == leapDiff) lunarLeap = true
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1

        return LunarDate(lunarDay, lunarMonth, lunarYear, lunarLeap, dayNumber)
    }

    private val stems = arrayOf("Giáp","Ất","Bính","Đinh","Mậu","Kỷ","Canh","Tân","Nhâm","Quý")
    private val branches = arrayOf("Tý","Sửu","Dần","Mão","Thìn","Tỵ","Ngọ","Mùi","Thân","Dậu","Tuất","Hợi")

    fun dayCanChi(julianDay: Int): String =
        "${stems[(julianDay + 9).mod(10)]} ${branches[(julianDay + 1).mod(12)]}"

    fun yearCanChi(lunarYear: Int): String =
        "${stems[(lunarYear + 6).mod(10)]} ${branches[(lunarYear + 8).mod(12)]}"
}
