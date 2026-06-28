package niko_scavengableindustries.utils

import com.fs.starfarer.api.campaign.FactionAPI
import niko_scavengableindustries.IndustryGenSpec
import niko_scavengableindustries.NSISettings
import java.util.ArrayList

object FactionUtils {
    fun FactionAPI.getSellableIndustryIds(): HashSet<IndustryGenSpec> {
        val sellable = HashSet<IndustryGenSpec>()
        val specs = NSISettings.industrySpecs

        for (ind in knownIndustries) {
            val spec = specs[ind] ?: continue
            if (spec.sellWeight <= 0f) continue
            sellable += spec
        }

        return sellable
    }
}