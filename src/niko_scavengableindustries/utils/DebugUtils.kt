package niko_scavengableindustries.utils

import com.fs.starfarer.api.Global
import org.apache.log4j.Level
import org.apache.log4j.Logger

object DebugUtils {
    val log: Logger = Global.getLogger(DebugUtils::class.java)

    init {
        log.level = Level.ALL
    }
}