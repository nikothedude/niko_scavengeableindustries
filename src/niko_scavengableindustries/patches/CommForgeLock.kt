package niko_scavengableindustries.patches

import com.fs.starfarer.api.campaign.econ.Industry
import indevo.industries.assembler.industry.CommodityForge
import niko_scavengableindustries.NSISettings
import niko_scavengableindustries.SalvageIndHelper
import niko_scavengableindustries.SalvageIndHelper.indIsSalvageAndKnown
import patchlib.api.context.AfterContext
import patchlib.api.match.ClassMatch
import patchlib.api.match.MethodMatch
import patchlib.api.patch.After
import patchlib.api.patch.Patch

//@Patch(target = ClassMatch(type = CommodityForge::class)) // REMOVED DUE TO PATCHLIB ALPHA
object CommForgeLock {
    @JvmStatic
    @After(target = MethodMatch(methodName = "isAvailableToBuild"))
    fun afterIsAvailableToBuild(context: AfterContext) {
        if (context.returnValue != true) {
            return
        }
        if (NSISettings.industrySpecs["IndEvo_AdAssem"] == null) {
            return
        }
        val ind = context.getInferredSelf<Industry>()
        if (!ind.market.faction.knownIndustries.contains("IndEvo_AdAssem"))
            context.returnValue = false
    }

    @JvmStatic
    @After(target = MethodMatch(methodName = "showWhenUnavailable"))
    fun afterShowWhenUnavailable(context: AfterContext) {
        if (context.returnValue != true) {
            return
        }
        if (NSISettings.industrySpecs["IndEvo_AdAssem"] == null) {
            return
        }
        val ind = context.getInferredSelf<Industry>()
        if (!ind.market.faction.knownIndustries.contains("IndEvo_AdAssem"))
            context.returnValue = false
    }
}