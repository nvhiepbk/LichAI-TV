package vn.ai.lich.tv

data class TVFengShuiInfo(
    val goodHours: List<String>,
    val bestDirection: String,
    val alternativeDirection: String?
)

object TVFengShui {
    private val HOANG_DAO = mapOf(
        0 to listOf("Tý","Sửu","Mão","Ngọ","Thân","Dậu"),
        1 to listOf("Dần","Mão","Tỵ","Thân","Tuất","Hợi"),
        2 to listOf("Tý","Sửu","Thìn","Tỵ","Mùi","Tuất"),
        3 to listOf("Tý","Dần","Mão","Ngọ","Mùi","Dậu"),
        4 to listOf("Dần","Thìn","Tỵ","Thân","Dậu","Hợi"),
        5 to listOf("Sửu","Thìn","Ngọ","Mùi","Tuất","Hợi"),
        6 to listOf("Tý","Sửu","Mão","Ngọ","Thân","Dậu"),
        7 to listOf("Dần","Mão","Tỵ","Thân","Tuất","Hợi"),
        8 to listOf("Tý","Sửu","Thìn","Tỵ","Mùi","Tuất"),
        9 to listOf("Tý","Dần","Mão","Ngọ","Mùi","Dậu"),
        10 to listOf("Dần","Thìn","Tỵ","Thân","Dậu","Hợi"),
        11 to listOf("Sửu","Thìn","Ngọ","Mùi","Tuất","Hợi")
    )

    private val RANGE = mapOf(
        "Tý" to "23–01","Sửu" to "01–03","Dần" to "03–05","Mão" to "05–07",
        "Thìn" to "07–09","Tỵ" to "09–11","Ngọ" to "11–13","Mùi" to "13–15",
        "Thân" to "15–17","Dậu" to "17–19","Tuất" to "19–21","Hợi" to "21–23"
    )

    private val HY = mapOf(
        "Giáp" to "Đông Bắc","Kỷ" to "Đông Bắc","Ất" to "Tây Bắc","Canh" to "Tây Bắc",
        "Bính" to "Tây Nam","Tân" to "Tây Nam","Đinh" to "Chính Nam","Nhâm" to "Chính Nam",
        "Mậu" to "Đông Nam","Quý" to "Đông Nam"
    )
    private val TAI = mapOf(
        "Giáp" to "Đông Nam","Ất" to "Đông Nam","Bính" to "Chính Đông","Đinh" to "Chính Đông",
        "Mậu" to "Chính Bắc","Kỷ" to "Chính Nam","Canh" to "Tây Nam","Tân" to "Tây Nam",
        "Nhâm" to "Chính Tây","Quý" to "Tây Bắc"
    )

    fun forJulianDay(jd: Int): TVFengShuiInfo {
        val can = arrayOf("Giáp","Ất","Bính","Đinh","Mậu","Kỷ","Canh","Tân","Nhâm","Quý")
        val chiIndex = (jd + 1).mod(12)
        val canName = can[(jd + 9).mod(10)]
        val hours = (HOANG_DAO[chiIndex] ?: emptyList()).map { "$it ${RANGE[it] ?: ""}" }
        val hy = HY[canName] ?: "Chưa xác định"
        val tai = TAI[canName]
        return TVFengShuiInfo(hours, hy, tai?.takeIf { it != hy })
    }
}
