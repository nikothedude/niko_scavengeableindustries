package niko_scavengableindustries.industries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.impl.campaign.econ.impl.Cryosanctum
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityCause2
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import org.magiclib.kotlin.getFactionMarkets

class NSICryosanctum: Cryosanctum() {

    override fun isAvailableToBuild(): Boolean {
        return market.faction.knowsIndustry(spec.id)
    }

    override fun isIndustry(): Boolean {
        return true
    }

}