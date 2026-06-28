package niko_scavengableindustries.industries

import com.fs.starfarer.api.impl.campaign.econ.impl.Cryosanctum

class NSICryosanctum: Cryosanctum() {

    override fun isAvailableToBuild(): Boolean {
        return market.faction.knowsIndustry(spec.id)
    }

    override fun isIndustry(): Boolean {
        return true
    }

}