package data.scripts

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import niko_scavengableindustries.NSFIBaseScript

abstract class DelayedExecution(
    val interval: IntervalUtil,
    var useDays: Boolean = true,
    var runIfPaused: Boolean = false
): NSFIBaseScript() {
    override fun startImpl() {
        Global.getSector().addScript(this)
    }

    override fun stopImpl() {
        Global.getSector().removeScript(this)
    }

    override fun runWhilePaused(): Boolean = runIfPaused

    override fun advance(amount: Float) {
        var amount = amount
        if (useDays) amount = Misc.getDays(amount)

        interval.advance(amount)
        if (interval.intervalElapsed()) {
            execute()
        }
    }

    private fun execute() {
        executeImpl()
        delete()
    }

    abstract fun executeImpl()
}