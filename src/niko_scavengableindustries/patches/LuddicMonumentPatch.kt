package niko_scavengableindustries.patches

import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.impl.campaign.econ.LuddicMajority
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseManager.AI_CORE_ADMIN_INTEREST
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCells
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel
import niko_scavengableindustries.ReflectionUtilsV2
import niko_scavengableindustries.industries.LuddicMonument
import patchlib.api.context.AfterContext
import patchlib.api.match.ClassMatch
import patchlib.api.match.MethodMatch
import patchlib.api.patch.After
import patchlib.api.patch.Patch
import sound.int

@Patch(target = ClassMatch(type = LuddicMajority::class))
object LuddicMonumentPatch {
    @JvmStatic
    @After(target = MethodMatch(methodName = "matchesBonusConditions", staticOnly = true))
    fun modifyMatchesBonusConditions(context: AfterContext) {
        if (context.returnValue == true) return
        val market = context.getArg(0) as? MarketAPI ?: return

        val ind = market.getIndustry("NSI_luddicMonument") ?: return
        if (!ind.isFunctional) return
        var interest = market.industries.sumOf { it.patherInterest.toInt() }
        if (market.admin.isAICore) interest += AI_CORE_ADMIN_INTEREST.toInt()

        var limit = LuddicMonument.INTEREST_LIMIT
        if (ind.aiCoreId == Commodities.ALPHA_CORE) limit += LuddicMonument.ALPHA_LIMIT

        if (interest <= limit) {
            context.returnValue = true
        }
    }
}