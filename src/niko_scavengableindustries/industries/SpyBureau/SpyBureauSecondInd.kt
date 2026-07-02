package niko_scavengableindustries.industries.SpyBureau

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry

class SpyBureauSecondInd: BaseIndustry() {
    override fun apply() {
        return
    }

    override fun isAvailableToBuild(): Boolean {
        return false
    }

    override fun showWhenUnavailable(): Boolean {
        return false
    }

    override fun isHidden(): Boolean {
        return true
    }
}