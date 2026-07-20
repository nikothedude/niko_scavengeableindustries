package niko_scavengableindustries.industries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier
import com.fs.starfarer.api.impl.campaign.econ.impl.Cryosanctum
import com.fs.starfarer.api.impl.campaign.ids.Strings
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventFactor
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityCause2
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityFactor
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator
import com.fs.starfarer.api.util.Misc
import niko_scavengableindustries.industries.SpyBureau.SpyBureau
import org.magiclib.kotlin.getFactionMarkets
import java.awt.Color

class NSICryosanctum: Cryosanctum() {

    companion object {
        const val TRUSTED_SOURCE_INCOME_MULT = 3f
    }

    override fun isAvailableToBuild(): Boolean {
        return market.faction.knowsIndustry(spec.id)
    }

    override fun isIndustry(): Boolean {
        return true
    }

    override fun applyIncomeAndUpkeep(sizeOverride: Float) {
        super.applyIncomeAndUpkeep(sizeOverride)

        if (Global.getSector().memoryWithoutUpdate.getBoolean("\$NSI_hasCryosanctumBuff")) {
            income.modifyMult("NSITrustedSource", TRUSTED_SOURCE_INCOME_MULT)
        }
    }

    class CrisisFactor(intel: HostileActivityEventIntel): BaseHostileActivityFactor(intel) {
        override fun shouldShow(intel: BaseEventIntel?): Boolean {
            return getNomios() != null && getThreateningSanctums().isNotEmpty()
        }

        fun getBiggestSanctum(): MarketAPI? {
            return getThreateningSanctums().maxByOrNull { it.size }
        }

        fun getNomios(): MarketAPI? = Global.getSector().economy.getMarket("nomios")
        fun getSanctums(): List<MarketAPI> = Global.getSector().playerFaction.getFactionMarkets().filter { it.hasIndustry("NSI_cryosanctum") }
        fun getThreateningSanctums(): List<MarketAPI> = getSanctums().filter { it.getIndustry("NSI_cryosanctum").isFunctional }
    }

}