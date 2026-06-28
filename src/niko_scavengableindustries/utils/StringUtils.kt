package niko_scavengableindustries.utils

object StringUtils {
    fun toPercent(num: Float): String {
        return String.format("%.0f", num * 100) + "%"
    }
}