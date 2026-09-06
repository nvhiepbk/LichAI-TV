package vn.ai.lich.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class MainActivity : Activity() {
    private val updateManifestUrl = "https://raw.githubusercontent.com/nvhiepbk/LichAI-TV/main/tv-update.json"
    private val defaultDownloadPage = "https://lich.ai.vn/download"

    private data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val title: String,
        val notes: List<String>,
        val downloaderCode: String,
        val downloadPage: String
    )
    private val vi = Locale("vi", "VN")
    private var selected = LocalDate.now()
    private var shownMonth = YearMonth.from(selected)
    private lateinit var dayPanel: LinearLayout
    private lateinit var monthTitle: TextView
    private lateinit var grid: GridLayout
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var monthGeneration = 0

    private data class DayData(val date: LocalDate, val lunar: LunarDate)

    private val returnToday = Runnable { goToday() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        // Safe boot: show the lightweight frame immediately. Heavy month lunar work is deferred.
        setContentView(buildScreen())
        renderDay(selected)
        loadMonthAsync(requestFocus = true)
        armReturnToday()
        // Home Recommendation is intentionally NOT published during startup in TV #02.
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onDestroy()
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
        header.addView(button("‹") { changeMonth(-1) }, LinearLayout.LayoutParams(dp(64), dp(52)))
        header.addView(button("Hôm nay") { goToday() }, LinearLayout.LayoutParams(dp(132), dp(52)).apply { marginStart = dp(10) })
        header.addView(button("›") { changeMonth(1) }, LinearLayout.LayoutParams(dp(64), dp(52)).apply { marginStart = dp(10) })
        header.addView(button("Cập nhật") { checkForUpdates(userInitiated = true) }, LinearLayout.LayoutParams(dp(132), dp(52)).apply { marginStart = dp(10) })
        right.addView(header)
        grid = GridLayout(this).apply { columnCount = 7; rowCount = 7; useDefaultMargins = false }
        right.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        root.addView(right, LinearLayout.LayoutParams(0, -1, 0.61f))
        return root
    }

    private fun renderDay(date: LocalDate) {
        val lunar = VietnameseLunar.fromSolar(date)
        val feng = TVFengShui.forJulianDay(lunar.julianDay)
        val weekday = date.format(DateTimeFormatter.ofPattern("EEEE", vi)).replaceFirstChar { it.uppercase(vi) }
        dayPanel.removeAllViews()
        dayPanel.addView(tv("LỊCH AI · LỊCH NGÀY", 17f, true).apply { setTextColor(Color.rgb(255, 209, 102)) })
        dayPanel.addView(tv(date.dayOfMonth.toString().padStart(2, '0'), 78f, true))
        dayPanel.addView(tv("$weekday · ${date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", 21f, true))
        dayPanel.addView(tv("🌙 ${lunar.day}/${lunar.month} Âm lịch${if (lunar.leap) " nhuận" else ""}", 27f, true).apply {
            setTextColor(Color.rgb(255, 209, 102)); setPadding(0, dp(18), 0, dp(12))
        })
        dayPanel.addView(tv("Năm ${VietnameseLunar.yearCanChi(lunar.year)}", 19f, false))
        dayPanel.addView(info("🕒 Giờ hoàng đạo", feng.goodHours.take(6).joinToString(" · ")))
        dayPanel.addView(info("🧭 Hướng tốt", listOfNotNull(feng.bestDirection, feng.alternativeDirection).distinct().joinToString(" · ")))
        dayPanel.addView(tv("Remote: ← ↑ ↓ → chọn ngày · OK xem chi tiết", 15f, false).apply {
            setTextColor(Color.LTGRAY); setPadding(0, dp(22), 0, 0)
        })
    }

    private fun loadMonthAsync(requestFocus: Boolean) {
        val month = shownMonth
        val generation = ++monthGeneration
        showMonthSkeleton(month)

        worker.execute {
            val days = (1..month.lengthOfMonth()).map { d ->
                val date = month.atDay(d)
                DayData(date, VietnameseLunar.fromSolar(date))
            }
            runOnUiThread {
                if (isFinishing || generation != monthGeneration || shownMonth != month) return@runOnUiThread
                renderMonth(month, days, requestFocus)
            }
        }
    }

    private fun showMonthSkeleton(month: YearMonth) {
        monthTitle.text = monthLabel(month)
        grid.removeAllViews()
        listOf("T2","T3","T4","T5","T6","T7","CN").forEach { label ->
            grid.addView(tv(label, 16f, true).apply { gravity = Gravity.CENTER; setTextColor(Color.LTGRAY) }, cellParams())
        }
        val offset = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
        repeat(offset) { grid.addView(Space(this), cellParams()) }
        repeat(month.lengthOfMonth()) {
            grid.addView(tv("·", 22f, false).apply { gravity = Gravity.CENTER; setTextColor(Color.DKGRAY) }, cellParams())
        }
    }

    private fun renderMonth(month: YearMonth, days: List<DayData>, requestFocus: Boolean) {
        monthTitle.text = monthLabel(month)
        grid.removeAllViews()
        listOf("T2","T3","T4","T5","T6","T7","CN").forEach { label ->
            grid.addView(tv(label, 16f, true).apply { gravity = Gravity.CENTER; setTextColor(Color.LTGRAY) }, cellParams())
        }
        val offset = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
        repeat(offset) { grid.addView(Space(this), cellParams()) }

        var focusCell: View? = null
        days.forEach { data ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isFocusable = true; isClickable = true
                setPadding(dp(4), dp(4), dp(4), dp(4)); background = CellBackground.normal()
                addView(tv(data.date.dayOfMonth.toString(), 24f, true).apply { gravity = Gravity.CENTER })
                addView(tv("${data.lunar.day}/${data.lunar.month}", 14f, false).apply { gravity = Gravity.CENTER; setTextColor(Color.rgb(255, 209, 102)) })
                if (TVContent.events(this@MainActivity, data.date, data.lunar).isNotEmpty() || TVContent.rituals(this@MainActivity, data.date, data.lunar).isNotEmpty()) {
                    addView(tv("●", 10f, true).apply { gravity = Gravity.CENTER; setTextColor(Color.rgb(255, 209, 102)) })
                }
                setOnFocusChangeListener { v, focused ->
                    v.background = if (focused) CellBackground.focused() else CellBackground.normal()
                    if (focused && selected != data.date) {
                        selected = data.date
                        renderDay(data.date)
                        armReturnToday()
                    }
                }
                setOnClickListener { selected = data.date; renderDay(data.date); showDetail(data.date) }
                tag = data.date
            }
            grid.addView(cell, cellParams())
            if (data.date == selected) focusCell = cell
        }
        if (requestFocus) focusCell?.post { focusCell?.requestFocus() }
    }

    private fun showDetail(date: LocalDate) {
        val lunar = VietnameseLunar.fromSolar(date)
        val events = TVContent.events(this, date, lunar)
        val historical = events.filter { it.category == "historical" }
        val cultural = events.filter { it.category != "historical" }
        val rituals = TVContent.rituals(this, date, lunar)
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        labels += "📋 Chi tiết ngày"
        actions += { showNativeDayDetail(date, lunar) }
        if (cultural.isNotEmpty()) {
            labels += "🏮 Sự kiện văn hóa (${cultural.size})"
            actions += { showEvents("Sự kiện văn hóa", cultural) }
        }
        if (historical.isNotEmpty()) {
            labels += "🏛️ Sự kiện lịch sử (${historical.size})"
            actions += { showEvents("Sự kiện lịch sử", historical) }
        }
        if (rituals.isNotEmpty()) {
            labels += "🙏 Văn khấn (${rituals.size})"
            actions += { showRituals(date, lunar, rituals) }
        }

        AlertDialog.Builder(this)
            .setTitle("${date.dayOfMonth}/${date.monthValue}/${date.year} · ${lunar.day}/${lunar.month} Âm lịch")
            .setItems(labels.toTypedArray()) { _, which -> actions[which].invoke() }
            .setNegativeButton("Đóng", null).show()
    }

    private fun showNativeDayDetail(date: LocalDate, lunar: LunarDate) {
        val d = TVDayDetails.forDate(date, lunar)
        val feng = TVFengShui.forJulianDay(lunar.julianDay)
        val text = buildString {
            append("DƯƠNG LỊCH\n${date.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", vi))}\n\n")
            append("ÂM LỊCH\n${lunar.day}/${lunar.month}/${lunar.year}${if (lunar.leap) " nhuận" else ""}\n\n")
            append("CAN CHI\nNgày ${d.canChiDay}\nTháng ${d.canChiMonth}\nNăm ${d.canChiYear}\n\n")
            append("NGŨ HÀNH · NẠP ÂM\n${d.napAm}\n\n")
            append("TIẾT KHÍ\n${d.tietKhi}\n\n")
            append("12 TRỰC\nTrực ${d.truc} — ${d.trucNote}\n\n")
            append("28 TÚ\n${d.tu} — ${d.tuNote}\n\n")
            append("GIỜ HOÀNG ĐẠO\n${feng.goodHours.joinToString(" · ")}\n\n")
            append("HƯỚNG TỐT\n${listOfNotNull(feng.bestDirection, feng.alternativeDirection).distinct().joinToString(" · ")}")
        }
        showLongText("Chi tiết ngày ${date.dayOfMonth}/${date.monthValue}", text)
    }

    private fun showEvents(title: String, events: List<TVEvent>) {
        // A phone tab becomes a TV menu. If a day has several events, list every
        // event first; selecting one opens its complete editorial article.
        if (events.size == 1) { showEvent(events.first()); return }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(events.map { "${it.icon} ${it.title}".trim() }.toTypedArray()) { _, i -> showEvent(events[i]) }
            .setNegativeButton("Quay lại", null).show()
    }

    private fun showEvent(e: TVEvent) {
        val text = buildString {
            if (e.subtitle.isNotBlank()) append(e.subtitle.trim()).append("\n\n")
            if (e.summary.isNotBlank()) append(e.summary.trim()).append("\n\n")
            if (e.description.isNotBlank() && e.description.trim() != e.summary.trim()) append("TÓM LƯỢC\n").append(e.description.trim()).append("\n\n")
            if (e.meaning.isNotBlank()) append("Ý NGHĨA\n").append(e.meaning.trim()).append("\n\n")
            e.sections.forEach { section ->
                append(section.icon).append(if(section.icon.isNotBlank()) " " else "")
                append(section.title.uppercase()).append("\n")
                append(section.content.trim()).append("\n\n")
            }
            if (e.tags.isNotEmpty()) append("CHỦ ĐỀ\n").append(e.tags.joinToString(" · ")).append("\n\n")
            if (e.sources.isNotEmpty()) {
                append("NGUỒN THAM KHẢO\n")
                e.sources.forEach { src -> append("• ").append(src.title); if(src.url.isNotBlank()) append("\n  ").append(src.url); append("\n") }
            }
        }.trim()
        showLongText("${e.icon} ${e.title}".trim(), text.ifBlank { "Thông tin sự kiện." })
    }

    private fun showRituals(date: LocalDate, lunar: LunarDate, rituals: List<TVRitual>) {
        // Preserve each ritual as its own item when several rituals match a date.
        if (rituals.size == 1) { showRitualMenu(rituals.first()); return }
        AlertDialog.Builder(this)
            .setTitle("Văn khấn")
            .setItems(rituals.map { it.title }.toTypedArray()) { _, i -> showRitualMenu(rituals[i]) }
            .setNegativeButton("Quay lại", null).show()
    }

    private fun showRitualMenu(r: TVRitual) {
        // Phone ritual cards/tabs become a D-pad menu on TV: preparation first,
        // then one menu item per prayer. The user chooses a prayer before reading.
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        val g = r.guidance
        if (r.summary.isNotBlank() || g.whenToUse.isNotBlank() || g.preparationNote.isNotBlank() || g.offerings.isNotEmpty() || g.notes.isNotEmpty()) {
            labels += "🧺 Chuẩn bị & lễ vật"
            actions += { showRitualPreparation(r) }
        }
        r.prayers.forEach { prayer ->
            labels += "🙏 ${prayer.title}"
            actions += { showPrayer(prayer) }
        }
        if (labels.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(r.title)
            .setItems(labels.toTypedArray()) { _, i -> actions[i].invoke() }
            .setNegativeButton("Quay lại", null).show()
    }

    private fun showRitualPreparation(r: TVRitual) {
        val g = r.guidance
        val text = buildString {
            if (r.summary.isNotBlank()) append(r.summary.trim()).append("\n\n")
            if (g.whenToUse.isNotBlank()) append("KHI NÀO DÙNG\n").append(g.whenToUse.trim()).append("\n\n")
            if (g.preparationNote.isNotBlank()) append("CHUẨN BỊ\n").append(g.preparationNote.trim()).append("\n\n")
            if (g.offerings.isNotEmpty()) { append("LỄ VẬT GỢI Ý\n"); g.offerings.forEach { append("• ").append(it.trim()).append("\n") }; append("\n") }
            if (g.notes.isNotEmpty()) { append("LƯU Ý\n"); g.notes.forEach { append("• ").append(it.trim()).append("\n") } }
        }.trim()
        showLongText("🧺 ${r.title}", text.ifBlank { "Không có yêu cầu chuẩn bị riêng." })
    }

    private fun showPrayer(p: TVPrayer) {
        val text = buildString { if (p.context.isNotBlank()) append(p.context.trim()).append("\n\n"); append(p.body.trim()) }
        showLongText(p.title, text)
    }

    private fun showLongText(title: String, text: String) {
        val scroll = ScrollView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            isVerticalScrollBarEnabled = true
            descendantFocusability = android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
            setPadding(dp(8), 0, dp(8), 0)
            setOnKeyListener { _, keyCode, event ->
                if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { smoothScrollBy(0, height * 2 / 3); true }
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> { smoothScrollBy(0, -height * 2 / 3); true }
                    android.view.KeyEvent.KEYCODE_PAGE_DOWN -> { pageScroll(android.view.View.FOCUS_DOWN); true }
                    android.view.KeyEvent.KEYCODE_PAGE_UP -> { pageScroll(android.view.View.FOCUS_UP); true }
                    else -> false
                }
            }
        }
        val content = tv(text, 19f, false).apply {
            setPadding(dp(28), dp(18), dp(28), dp(40)); setLineSpacing(0f, 1.18f)
        }
        scroll.addView(content)
        val dialog = AlertDialog.Builder(this)
            .setTitle("$title  •  ↑↓ để cuộn")
            .setView(scroll)
            .setPositiveButton("Quay lại", null)
            .create()
        dialog.setOnShowListener {
            // Give D-pad to the document instead of the dialog button.
            scroll.post { scroll.requestFocus() }
        }
        dialog.show()
    }

    private fun changeMonth(delta: Long) {
        shownMonth = shownMonth.plusMonths(delta)
        selected = shownMonth.atDay(1)
        renderDay(selected)
        loadMonthAsync(requestFocus = true)
        armReturnToday()
    }

    private fun goToday() {
        selected = LocalDate.now()
        shownMonth = YearMonth.from(selected)
        renderDay(selected)
        loadMonthAsync(requestFocus = true)
        armReturnToday()
    }

    private fun monthLabel(month: YearMonth) =
        "${month.month.getDisplayName(java.time.format.TextStyle.FULL, vi).replaceFirstChar { it.uppercase(vi) }} ${month.year}"

    private fun armReturnToday() {
        handler.removeCallbacks(returnToday)
        handler.postDelayed(returnToday, 60_000L)
    }

    private fun cellParams() = GridLayout.LayoutParams().apply {
        width = 0; height = 0
        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        setMargins(dp(4), dp(4), dp(4), dp(4))
    }
    private fun tv(text: String, size: Float, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.WHITE)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    private fun info(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(0, dp(13), 0, 0)
        addView(tv(label, 16f, true).apply { setTextColor(Color.LTGRAY) })
        addView(tv(value, 17f, false).apply { setPadding(0, dp(4), 0, 0) })
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isFocusable = true; setOnClickListener { action() }
    }

    private fun currentVersionCode(): Int = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    } catch (_: Exception) { 0 }

    private fun checkForUpdates(userInitiated: Boolean) {
        val progress = if (userInitiated) AlertDialog.Builder(this)
            .setTitle("Kiểm tra cập nhật")
            .setMessage("Đang kiểm tra phiên bản Lịch AI TV mới…")
            .setCancelable(false).create().also { it.show() } else null

        worker.execute {
            try {
                val conn = (URL(updateManifestUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000; readTimeout = 7000
                    requestMethod = "GET"; setRequestProperty("Cache-Control", "no-cache")
                }
                val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                conn.disconnect()
                val j = JSONObject(raw)
                val notesJson = j.optJSONArray("notes")
                val notes = mutableListOf<String>()
                if (notesJson != null) for (i in 0 until notesJson.length()) notes += notesJson.optString(i)
                val info = UpdateInfo(
                    j.optInt("versionCode", 0), j.optString("versionName", ""),
                    j.optString("title", "Có phiên bản mới"), notes,
                    j.optString("downloaderCode", ""),
                    j.optString("downloadPage", defaultDownloadPage).ifBlank { defaultDownloadPage }
                )
                runOnUiThread {
                    progress?.dismiss()
                    if (info.versionCode > currentVersionCode()) showUpdateAvailable(info)
                    else if (userInitiated) AlertDialog.Builder(this)
                        .setTitle("Lịch AI TV đã mới nhất")
                        .setMessage("Phiên bản đang cài hiện chưa có bản cập nhật mới.")
                        .setPositiveButton("OK", null).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress?.dismiss()
                    if (userInitiated) AlertDialog.Builder(this)
                        .setTitle("Chưa kiểm tra được cập nhật")
                        .setMessage("TV chưa kết nối được máy chủ cập nhật. Hãy kiểm tra Internet rồi thử lại.")
                        .setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun showUpdateAvailable(info: UpdateInfo) {
        val changes = if (info.notes.isEmpty()) "Có phiên bản Lịch AI TV mới."
        else info.notes.joinToString("\n") { "• $it" }
        AlertDialog.Builder(this)
            .setTitle("${info.title} · ${info.versionName}")
            .setMessage("TÍNH NĂNG / THAY ĐỔI\n\n$changes")
            .setPositiveButton("Tiếp tục cập nhật") { _, _ -> showUpdateInstructions(info) }
            .setNegativeButton("Để sau", null)
            .show()
    }

    private fun showUpdateInstructions(info: UpdateInfo) {
        val codeText = if (info.downloaderCode.isBlank())
            "Mã Downloader đang được cập nhật trên trang tải." else info.downloaderCode
        val message = """
            CÁCH 1 · DOWNLOADER
            1. Mở ứng dụng Downloader trên Android TV.
            2. Nhập mã: $codeText
            3. Chọn Go → tải APK → Install để cài đè.

            CÁCH 2 · TRANG TẢI LỊCH AI
            Mở ${info.downloadPage}
            Trang tải luôn hiển thị link APK TV và mã Downloader hiện hành.

            Dữ liệu và cài đặt của Lịch AI TV được giữ lại khi cài đè đúng gói chính thức.
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Cập nhật Lịch AI TV")
            .setMessage(message)
            .setPositiveButton("Mở trang tải") { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadPage))) } catch (_: Exception) {}
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
