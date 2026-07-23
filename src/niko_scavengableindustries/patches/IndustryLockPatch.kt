package niko_scavengableindustries.patches

import com.fs.starfarer.api.campaign.econ.Industry
import niko_scavengableindustries.NSISettings
import niko_scavengableindustries.SalvageIndHelper
import niko_scavengableindustries.SalvageIndHelper.indIsSalvageAndKnown
import patchlib.api.context.AfterContext
import patchlib.api.patch.Patch
import patchlib.api.match.ClassMatch
import patchlib.api.match.MethodMatch
import patchlib.api.patch.After

@Patch(target = ClassMatch(subtype = Industry::class))
object IndustryLockPatch {
    @JvmStatic
    @After(target = MethodMatch(methodName = "isAvailableToBuild"))
    fun afterIsAvailableToBuild(context: AfterContext) {
        if (context.returnValue != true) {
            return
        }
        val ind = context.getInferredSelf<Industry>()
        if (!indIsSalvageAndKnown(ind)) {
            context.returnValue = false
        }
    }

    @JvmStatic
    @After(target = MethodMatch(methodName = "showWhenUnavailable"))
    fun afterShowWhenUnavailable(context: AfterContext) {
        if (context.returnValue != true) {
            return
        }
        val ind = context.getInferredSelf<Industry>()
        if (!indIsSalvageAndKnown(ind)) {
            context.returnValue = false
        }
    }

    @JvmStatic
    @After(target = MethodMatch(methodName = "getUnavailableReason"))
    fun afterGetUnavailableReason(context: AfterContext) {
        val returnValue = context.returnValue as? String
        if (returnValue != null) {
            return
        }
        val ind = context.getInferredSelf<Industry>()
        if (!indIsSalvageAndKnown(ind)) {
            context.returnValue = SalvageIndHelper.getKnownError(ind.market.faction, ind.spec)
        }
    }

    @JvmStatic
    @After(target = MethodMatch(methodName = "canUpgrade"))
    fun afterCanUpgrade(context: AfterContext) {
        if (context.returnValue != true) return

        val ind = context.getInferredSelf<Industry>()
        val spec = ind.spec
        val upgradeId = spec.upgrade ?: return
        val salvageSpec = NSISettings.industrySpecs[upgradeId] ?: return

        if (ind.market.faction.knowsIndustry(upgradeId)) return

        context.returnValue = false
    }
}