package niko_scavengableindustries.industries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier
import com.fs.starfarer.api.impl.campaign.econ.LuddicMajority
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.intel.events.LuddicChurchHostileActivityFactor
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import niko_scavengableindustries.industries.NebulaSiphoner.Companion.ALPHA_CORE_STORAGE_INCR
import niko_scavengableindustries.industries.NebulaSiphoner.Companion.FRONTEND_MOL_MULT

// makes luddic majority toggle as long as you have less than x pather interest
// increases immigration from the church
// none of this applies if the deal was made with the church

class LuddicMonument: BaseIndustry(), MarketImmigrationModifier {
    companion object {
        const val INTEREST_LIMIT = 10f
        const val ALPHA_LIMIT = 5f
        const val IMMIGRATION_BASE = 3f
        const val IMMIGRATION_IMPROVED = 1f
    }

    override fun apply() {
        super.apply(true)

        val size = market.size
        demand(Commodities.ORGANICS, size - 1)
        demand(Commodities.CREW, size - 1)

        if (!isFunctional) return
        market.addTransientImmigrationModifier(this)
        market.primaryEntity?.addTag(Tags.LUDDIC_SHRINE)
    }

    override fun unapply() {
        super.unapply()

        market.removeTransientImmigrationModifier(this)
        market.primaryEntity?.removeTag(Tags.LUDDIC_SHRINE)
    }

    override fun isFunctional(): Boolean {
        return (!market.faction.isPlayerFaction || !LuddicChurchHostileActivityFactor.isMadeDeal()) && super.isFunctional()
    }

    override fun modifyIncoming(
        market: MarketAPI?,
        incoming: PopulationComposition?
    ) {
        if (market == null || incoming == null) return
        if (!LuddicMajority.matchesBonusConditions(market)) return
        if (!isFunctional) return

        val bonus: Float = getImmigrationBonus()
        if (bonus > 0) {
            incoming.add(Factions.LUDDIC_CHURCH, bonus)
            incoming.weight.modifyFlat(modId, bonus, "Luddic immigration (${nameForModifier})")
        }
    }

    fun getImmigrationBonus(): Float {
        var base = IMMIGRATION_BASE
        if (isImproved) base += IMMIGRATION_IMPROVED
        val bonus = base * market.size
        return bonus
    }

    override fun addPostSupplySection(
        tooltip: TooltipMakerAPI?,
        hasSupply: Boolean,
        mode: Industry.IndustryTooltipMode?
    ) {
        super.addPostSupplySection(tooltip, hasSupply, mode)

        if (tooltip == null) return

        if (LuddicChurchHostileActivityFactor.isMadeDeal()) {
            tooltip.addPara(
                "While the monument would usually attract the easily impressed, your deal with the Knights has made sure it is either impractical or " +
                "condemned.",
                5f,
            ).color = Misc.getNegativeHighlightColor()
            return
        }

        tooltip.addPara(
            "Enables %s to be active even with %s, as long as %s remains below or equal to %s.",
            10f,
            Misc.getHighlightColor(),
            "Luddic Majority", "industrial buildings", "pather interest", "${INTEREST_LIMIT.toInt()}"
        ).setHighlightColors(
            Global.getSector().getFaction(Factions.LUDDIC_CHURCH).baseUIColor,
            Misc.getHighlightColor(),
            Misc.getHighlightColor(),
            Misc.getHighlightColor()
        )

        tooltip.addPara(
            "Additionally, increases immigration from %s if %s is active.",
            10f,
            Global.getSector().getFaction(Factions.LUDDIC_CHURCH).baseUIColor,
            "luddic pilgrims", "luddic majority"
        )
    }

    override fun canImprove(): Boolean {
        return true
    }

    override fun addImproveDesc(info: TooltipMakerAPI?, mode: Industry.ImprovementDescriptionMode?) {
        info?.addPara(
            "Improves immigration rate.",
            0f
        )
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
                        "Ironically increases pather interest limit to %s.", 0f, highlight,
                "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION, "${(INTEREST_LIMIT + ALPHA_LIMIT).toInt()}"
            )
            tooltip.addImageWithText(opad)
            return
        }

        tooltip.addPara(
            pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                    "Ironically increases pather interest limit to %s.", opad, highlight,
            "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION, "${(INTEREST_LIMIT + ALPHA_LIMIT).toInt()}"
        )
    }

    override fun getPatherInterest(): Float {
        return 0f
    }

}