package niko_scavengableindustries.industries.SpyBureau

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.impl.campaign.ids.Industries
import com.fs.starfarer.api.impl.campaign.ids.Strings
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventFactor
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator
import com.fs.starfarer.api.util.Misc
import org.magiclib.kotlin.getFactionMarkets
import java.awt.Color

class SpyBureauDefenseFactor: BaseEventFactor() {

    override fun getMainRowTooltip(intel: BaseEventIntel?): TooltipCreator {
        return object : BaseFactorTooltip() {
            override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any?) {
                val h = Misc.getHighlightColor()
                val opad = 10f

                val bureau = getBureau() ?: return
                val mult = bureau.getHAMultString()

                tooltip.addPara(
                    "Your operatives work from the shadows to counteract hostile intrusions of all shapes and sizes, whether " +
                            "through negotiation, subterfuge, or more 'black arts'.",
                    0f
                )

                if (bureau.isFunctional && bureau.getHAMult() < 1f) {
                    tooltip.addPara(
                        "All progress is reduced by %s.",
                        opad,
                        Misc.getHighlightColor(),
                        mult
                    )
                } else {
                    tooltip.addPara(
                        "Unfortunately, recent disruptions have resulted in total disorganization among your spies. %s.",
                        opad,
                        Misc.getNegativeHighlightColor(),
                        "Your Spy Bureau has no effect at current."
                    )
                }
            }
        }
    }

    fun getBureau(): SpyBureau? {
        val markets = Global.getSector().playerFaction.getFactionMarkets() ?: return null
        val withBureau = markets.filter { it.hasIndustry("NSI_SpyBureau") }
        val bureauPlanet = withBureau.maxByOrNull { it.size }
        return bureauPlanet?.getIndustry("NSI_SpyBureau") as? SpyBureau
    }

    override fun shouldShow(intel: BaseEventIntel?): Boolean {
        //HAColonyDefenseData data = getDefenseData(intel);
        return (getBureau() != null)
    }


    override fun getAllProgressMult(intel: BaseEventIntel?): Float {
        return getBureau()?.getHAMult() ?: 1f
    }


    override fun getProgressColor(intel: BaseEventIntel?): Color? {
        val mult = getBureau()?.getHAMult() ?: 1f
        if (mult < 1) {
            return Misc.getPositiveHighlightColor()
        } else if (mult > 1) {
            return Misc.getNegativeHighlightColor()
        }
        return Misc.getHighlightColor() // no text anyway
    }

    override fun getProgressStr(intel: BaseEventIntel?): String {
        val mult = getBureau()?.getHAMult() ?: 1f
        if (mult != 1f) {
            return Strings.X + Misc.getRoundedValueMaxOneAfterDecimal(mult)
        }
        return ""
    }

    override fun getDesc(intel: BaseEventIntel?): String? {
        val bureau = getBureau() ?: return "ERROR"

        return "${bureau.market.name} ${bureau.nameForModifier}"
    }

    override fun getDescColor(intel: BaseEventIntel?): Color? {
        val bureau = getBureau() ?: return Misc.getGrayColor()
        if (!bureau.isFunctional || bureau.getHAMult() >= 1f) {
            return Misc.getGrayColor()
        }
        return super.getDescColor(intel)
    }

}