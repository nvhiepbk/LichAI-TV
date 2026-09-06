package vn.ai.lich.tv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object CellBackground {
    fun normal() = GradientDrawable().apply {
        setColor(Color.rgb(25, 35, 48)); cornerRadius = 14f
        setStroke(1, Color.rgb(53, 67, 84))
    }
    fun focused() = GradientDrawable().apply {
        setColor(Color.rgb(58, 73, 91)); cornerRadius = 14f
        setStroke(4, Color.rgb(255, 209, 102))
    }
}
