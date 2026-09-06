package vn.ai.lich.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {
    private val vi = Locale("vi", "VN")
    private var selected = LocalDate.now()
    private var shownMonth = YearMonth.from(selected)
    private lateinit var dayPanel: LinearLayout
    private lateinit var monthTitle: TextView
    private lateinit var grid: GridLayout
    private val handler = Handler(Looper.getMainLooper())
    private val returnToday = Runnable {
        selected = LocalDate.now(); shownMonth = YearMonth.from(selected); renderAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(buildScreen())
        renderAll()
        handler.postDelayed({ HomeRecommendation.publish(this) }, 1200)
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(14, 20, 28))
            setPadding(dp(34), dp(28), dp(34), dp(28))
        }
        dayPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(20), dp(30), dp(20))
            setBackgroundColor(Color.rgb(24, 33, 45))
        }
        root.addView(dayPanel, LinearLayout.LayoutParams(0, -1, 0.39f).apply { rightMargin = dp(24) })

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        monthTitle = tv("", 28f, true)
        header.addView(monthTitle, LinearLayout.LayoutParams(0, dp(56), 1f))
        header.addView(button("‹", -1) { changeMonth(-1) }, LinearLayout.LayoutParams(dp(64), dp(52)))
        header.addView(button("Hôm nay", 0) { goToday() }, LinearLayout.LayoutParams(dp(132), dp(52)).apply { marginStart = dp(10) })
        header.addView(button("›", 1) { changeMonth(1) }, LinearLayout.LayoutParams(dp(64), dp(52)).apply { marginStart = dp(10) })
        right.addView(header)
        grid = GridLayout(this).apply { columnCount = 7; rowCount = 7; useDefaultMargins = false }
        right.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        root.addView(right, LinearLayout.LayoutParams(0, -1, 0.61f))
        return root
    }

    private fun renderAll() { renderDay(); renderMonth(); armReturnToday() }

    private fun renderDay() {
        dayPanel.removeAllViews()
        val lunar = VietnameseLunar.solarToLunar(selected)
        val jd = VietnameseLunar.jdFromDate(selected.dayOfMonth, selected.monthValue, selected.year)
        val feng = TVFengShui.forJulianDay(jd)
        val weekday = selected.format(DateTimeFormatter.ofPattern("EEEE", vi)).replaceFirstChar { it.uppercase(vi) }
        dayPanel.addView(tv("LỊCH AI · LỊCH NGÀY", 17f, true).apply { setTextColor(Color.rgb(255, 209, 102)) })
        dayPanel.addView(tv(selected.dayOfMonth.toString().padStart(2, '0'), 78f, true))
        dayPanel.addView(tv("$weekday · ${selected.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", 21f, true))
        dayPanel.addView(tv("🌙 ${lunar.day}/${lunar.month} Âm lịch${if (lunar.leap) " nhuận" else ""}", 27f, true).apply {
            setTextColor(Color.rgb(255, 209, 102)); setPadding(0, dp(18), 0, dp(12))
        })
        dayPanel.addView(tv("Năm ${VietnameseLunar.yearCanChi(lunar.year)}", 19f, false))
        dayPanel.addView(info("🕒 Giờ hoàng đạo", feng.goodHours.take(6).joinToString(" · ")))
        val dirs = listOfNotNull(feng.bestDirection, feng.alternativeDirection).distinct().joinToString(" · ")
        dayPanel.addView(info("🧭 Hướng tốt", dirs))
        dayPanel.addView(TextView(this).apply { text = "Remote: ← ↑ ↓ → chọn ngày · OK xem chi tiết"; textSize = 15f; setTextColor(Color.LTGRAY); setPadding(0, dp(22), 0, 0) })
    }

    private fun renderMonth() {
        grid.removeAllViews()
        monthTitle.text = "${shownMonth.month.getDisplayName(java.time.format.TextStyle.FULL, vi).replaceFirstChar { it.uppercase(vi) }} ${shownMonth.year}"
        listOf("T2","T3","T4","T5","T6","T7","CN").forEach { label ->
            grid.addView(tv(label, 16f, true).apply { gravity = Gravity.CENTER; setTextColor(Color.LTGRAY) }, cellParams())
        }
        val first = shownMonth.atDay(1)
        val offset = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        repeat(offset) { grid.addView(Space(this), cellParams()) }
        for (d in 1..shownMonth.lengthOfMonth()) {
            val date = shownMonth.atDay(d)
            val lunar = VietnameseLunar.solarToLunar(date)
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isFocusable = true; isClickable = true
                setPadding(dp(4), dp(4), dp(4), dp(4)); background = CellBackground.normal()
                addView(tv(d.toString(), 24f, true).apply { gravity = Gravity.CENTER })
                addView(tv("${lunar.day}/${lunar.month}", 14f, false).apply { gravity = Gravity.CENTER; setTextColor(Color.rgb(255, 209, 102)) })
                setOnFocusChangeListener { v, focused -> v.background = if (focused) CellBackground.focused() else CellBackground.normal(); if (focused) { selected = date; renderDay(); armReturnToday() } }
                setOnClickListener { selected = date; renderDay(); showDetail(date) }
                tag = date
            }
            grid.addView(cell, cellParams())
            if (date == selected) cell.post { cell.requestFocus() }
        }
    }

    private fun showDetail(date: LocalDate) {
        val lunar = VietnameseLunar.solarToLunar(date)
        val jd = VietnameseLunar.jdFromDate(date.dayOfMonth, date.monthValue, date.year)
        val feng = TVFengShui.forJulianDay(jd)
        val text = buildString {
            append("Dương lịch: ${date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}\n")
            append("Âm lịch: ${lunar.day}/${lunar.month}/${lunar.year}${if (lunar.leap) " nhuận" else ""}\n")
            append("Năm: ${VietnameseLunar.yearCanChi(lunar.year)}\n\n")
            append("Giờ hoàng đạo: ${feng.goodHours.joinToString(" · ")}\n\n")
            append("Hướng tốt: ${listOfNotNull(feng.bestDirection, feng.alternativeDirection).distinct().joinToString(" · ")}")
        }
        AlertDialog.Builder(this)
            .setTitle("Chi tiết ngày ${date.dayOfMonth}/${date.monthValue}")
            .setMessage(text)
            .setPositiveButton("Mở chi tiết đầy đủ") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lich.ai.vn/")))
            }
            .setNegativeButton("Quay lại", null).show()
    }

    private fun changeMonth(delta: Long) { shownMonth = shownMonth.plusMonths(delta); selected = shownMonth.atDay(1); renderAll() }
    private fun goToday() { selected = LocalDate.now(); shownMonth = YearMonth.from(selected); renderAll() }
    private fun armReturnToday() { handler.removeCallbacks(returnToday); handler.postDelayed(returnToday, 60_000L) }

    private fun cellParams() = GridLayout.LayoutParams().apply { width = 0; height = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(4), dp(4), dp(4), dp(4)) }
    private fun tv(text: String, size: Float, bold: Boolean) = TextView(this).apply { this.text = text; textSize = size; setTextColor(Color.WHITE); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD) }
    private fun info(label: String, value: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(13), 0, 0); addView(tv(label, 16f, true).apply { setTextColor(Color.LTGRAY) }); addView(tv(value, 17f, false).apply { setPadding(0, dp(4), 0, 0) }) }
    private fun button(label: String, delta: Int, action: () -> Unit) = Button(this).apply { text = label; isFocusable = true; setOnClickListener { action() } }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
