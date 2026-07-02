package niko_scavengableindustries.industries.SpyBureau

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import niko_scavengableindustries.industries.SpyBureau.SpyBureau.Companion.DESTAB_DIST_LY
import org.magiclib.kotlin.getFactionMarkets

/// Requires a bureau
/// Destabilizes/reduces access of nearbyu hostile markets (5 LY?)
/// 1 per sys
/// Structure, but is expensive
class SpyOutpost: BaseIndustry() {
    companion object {
        fun destabilizeNearbyMarkets(ourMarket: MarketAPI, distLY: Float, deficitMult: Float, stability: Float = STAB_MALUS): HashSet<MarketAPI> {
            val affected = HashSet<MarketAPI>()
            val bonusText = if (deficitMult < 1) " (Shortages)" else ""
            for (market in Global.getSector().economy.marketsCopy) {
                if (!SpyBureau.marketIsValidTarget(market, ourMarket.faction)) continue
                val dist = Misc.getDistanceLY(market.primaryEntity, ourMarket.primaryEntity)
                if (dist > distLY) continue

                market.stability.modifyFlat("NSI_SpySabotage", (stability * deficitMult).toInt().toFloat(), "${ourMarket.faction.displayName} sabotage$bonusText")
                market.accessibilityMod.modifyFlat("NSI_SpySabotage", ACCESS_MALUS * deficitMult, "${ourMarket.faction.displayName} sabotage$bonusText")
                affected += market
            }
            return affected
        }

        fun resetDestabilization(markets: Collection<MarketAPI>) {
            for (market in markets) {
                market.stability.unmodify("NSI_SpySabotage")
                market.accessibilityMod.unmodify("NSI_SpySabotage")
            }
        }

        const val STAB_MALUS = -2f
        const val ACCESS_MALUS = -0.2f
        const val ALPHA_STABILITY_REDUCTION = -3f
    }

    var affectedSabotageMarkets = HashSet<MarketAPI>()

    override fun apply() {
        super.apply(true)

        val size = market.size
        demand(Commodities.SUPPLIES, size - 1)
        demand(Commodities.CREW, size)

        if (!isFunctional) return

        val saboDeficitMult = getDeficitMult(
            Commodities.SUPPLIES,
            Commodities.CREW
        )

        affectedSabotageMarkets = destabilizeNearbyMarkets(
            market,
            DESTAB_DIST_LY,
            saboDeficitMult,
            if (aiCoreId == Commodities.ALPHA_CORE) ALPHA_STABILITY_REDUCTION else STAB_MALUS
        )
    }

    override fun unapply() {
        super.unapply()

        resetDestabilization(affectedSabotageMarkets)
        affectedSabotageMarkets.clear()
    }

    override fun addPostDemandSection(
        tooltip: TooltipMakerAPI?,
        hasDemand: Boolean,
        mode: Industry.IndustryTooltipMode?
    ) {
        super.addPostDemandSection(tooltip, hasDemand, mode)
        if (tooltip == null) return

        tooltip.addPara(
            "Reduces stability and accessibility of hostile colonies within %s by %s and %s respectively. This %s decivilize colonies.",
            10f,
            Misc.getHighlightColor(),
            "${DESTAB_DIST_LY.toInt()} LY", "${-STAB_MALUS.toInt()}", "${(-ACCESS_MALUS * 100f).toInt()}%", "can"
        )

        val parent = getParent()
        if (parent == null) {
            tooltip.addPara(
                "No governing Spy Bureau exists! The outpost cant function!",
                10f
            ).color = Misc.getNegativeHighlightColor()
        } else if (parent.market.size < market.size) {
            tooltip.addPara(
                "The governing Spy Bureau is too small to support an outpost on ${market.name}!",
                10f
            ).color = Misc.getNegativeHighlightColor()
        }
    }

    fun getParent(): SpyBureau? {
        val hasIndustry = market.faction.getFactionMarkets().find { it.hasIndustry("NSI_SpyBureau") } ?: return null
        return hasIndustry.getIndustry("NSI_SpyBureau") as? SpyBureau
    }

    override fun isFunctional(): Boolean {
        val parent = getParent() ?: return false
        if (parent.market.size < market.size) return false
        return super.isFunctional
    }

    override fun isAvailableToBuild(): Boolean {
        if (!market.faction.knowsIndustry("NSI_SpyBureau")) return false
        val parent = getParent() ?: return false
        if (parent.market.size < market.size) return false

        return super.isAvailableToBuild()
    }

    override fun getUnavailableReason(): String? {
        val parent = getParent() ?: return "No spy Bureau present"
        if (parent.market.size < market.size) return "Spy Bureau must be on a larger colony"

        return super.getUnavailableReason()
    }

    override fun showWhenUnavailable(): Boolean {
        if (!market.faction.knowsIndustry("NSI_SpyBureau")) return false

        return super.showWhenUnavailable()
    }

    override fun addAlphaCoreDescription(tooltip: TooltipMakerAPI?, mode: AICoreDescriptionMode?) {
        if (tooltip == null) return

        val opad = 10f
        val highlight = Misc.getHighlightColor()

        var pre = "Alpha-level AI core currently assigned. "
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. "
        }
        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP || mode == AICoreDescriptionMode.MANAGE_CORE_TOOLTIP) {
            val coreSpec = Global.getSettings().getCommoditySpec(aiCoreId)
            val text = tooltip.beginImageWithText(coreSpec.iconName, 48f)
            text.addPara(
                pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                        "Increases stability reduction to %s.", 0f, highlight,
                "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION, "${-ALPHA_STABILITY_REDUCTION.toInt()}"
            )
            tooltip.addImageWithText(opad)
            return
        }

        tooltip.addPara(
            pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                    "Increases stability reduction to %s.", opad, highlight,
            "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION, "${-ALPHA_STABILITY_REDUCTION.toInt()}"
        )
    }
}