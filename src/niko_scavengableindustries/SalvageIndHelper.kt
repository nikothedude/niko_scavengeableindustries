package niko_scavengableindustries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.loading.IndustrySpecAPI

object SalvageIndHelper {
    fun getKnownError(fac: FactionAPI, spec: IndustrySpecAPI): String? {
        if (!fac.knownIndustries.contains(spec.id)) return "Must be learned"
        return null
    }

    fun indIsSalvageAndKnown(ind: Industry): Boolean {
        val salvageSpec = NSISettings.industrySpecs[ind.spec.id] ?: return true
        if (ind.market.faction.knownIndustries.contains(ind.spec.id)) return true
        return false
    }
}