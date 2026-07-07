package niko_scavengableindustries.industries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode
import com.fs.starfarer.api.campaign.econ.Industry.IndustryTooltipMode
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.Industries
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import niko_scavengableindustries.utils.StringUtils.toPercent
import org.magiclib.kotlin.isMilitary

/// Trains marines stationed on the planet. Ground defense bonus (Minor). Expensive. (Officers you get from this planet are higher level?)
class TrainingCamp: BaseIndustry() {

    companion object {
        const val GROUND_DEFENSE_MULT = 1.25f
        const val ALPHA_CORE_TRAINING_MULT = 0.25f
        const val IMPROVED_TRANING_MULT = 0.25f
    }

    var storedOfficer: PersonAPI? = null

    override fun apply() {
        super.apply(true)

        val size = market.size
        demand(Commodities.SUPPLIES, size + 1)
        demand(Commodities.FUEL, size - 1)
        demand(Commodities.SHIPS, size - 1)
        demand(Commodities.CREW, size - 2)
        demand(Commodities.HAND_WEAPONS, size - 2)

        val deficit = getDeficitMult(
            Commodities.SUPPLIES,
            Commodities.FUEL,
            Commodities.SHIPS,
            Commodities.CREW,
            Commodities.HAND_WEAPONS
        )

        market.stats.dynamic.getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(id, 1f + ((GROUND_DEFENSE_MULT - 1) * deficit), nameForModifier)
        return
    }

    override fun unapply() {
        super.unapply()

        market.stats.dynamic.getMod(Stats.GROUND_DEFENSES_MOD).unmodify(id)
    }

    override fun advance(amount: Float) {
        super.advance(amount)
        if (!market.isPlayerOwned) return
        if (!isFunctional) return

        for (submarket in market.submarketsCopy) {
            val cargo = submarket.cargo
            val marines = cargo.stacksCopy.firstOrNull { it.isMarineStack } ?: continue

            val exp = PlayerFleetPersonnelTracker.getInstance().getDroppedOffAt(
                Commodities.MARINES,
                market.primaryEntity,
                submarket,
                true
            )

            var extraMult = 1f
            if (aiCoreId == Commodities.ALPHA_CORE) extraMult += ALPHA_CORE_TRAINING_MULT
            if (isImproved) extraMult += IMPROVED_TRANING_MULT

            val shortageMult = getDeficitMult(
                Commodities.FUEL,
                Commodities.SUPPLIES,
                Commodities.CREW,
                Commodities.SHIPS,
                Commodities.HAND_WEAPONS
            )

            exp.data.num = marines.size
            exp.data.addXP((0.01f * amount * extraMult) * shortageMult)
            exp.data.clampXP()
        }

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
                        "Improves marine rate of training.", 0f, highlight,
                "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION,
                "" + SUPPLY_BONUS
            )
            tooltip.addImageWithText(opad)
            return
        }

        tooltip.addPara(
            pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                    "Improves marine rate of training.", opad, highlight,
            "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION,
            "" + SUPPLY_BONUS
        )
    }

    override fun canImprove(): Boolean {
        return true
    }

    override fun addImproveDesc(info: TooltipMakerAPI?, mode: Industry.ImprovementDescriptionMode?) {
        if (info == null) return

        info.addPara(
            "Improves marine training rate by %s.",
            0f,
            Misc.getHighlightColor(),
            toPercent(IMPROVED_TRANING_MULT)
        )
    }

    override fun addPostDemandSection(
        tooltip: TooltipMakerAPI?,
        hasDemand: Boolean,
        mode: IndustryTooltipMode?
    ) {

        //if (mode == IndustryTooltipMode.NORMAL && isFunctional()) {
        if (mode != IndustryTooltipMode.NORMAL || isFunctional()) {
            addStabilityPostDemandSection(tooltip, hasDemand, mode)

            val hb = Industries.HEAVYBATTERIES == getId()
            val bonus = GROUND_DEFENSE_MULT - 1
            addGroundDefensesImpactSection(
                tooltip,
                bonus,
                Commodities.SUPPLIES,
                Commodities.FUEL,
                Commodities.SHIPS,
                Commodities.HAND_WEAPONS,
                Commodities.CREW
            )

            tooltip?.addPara(
                "Performs %s on marines stationed within %s or %s of the colony, %s.",
                10f,
                Misc.getHighlightColor(),
                "special training", "submarkets", "storage", "slowly improving their experience"
            )
        }
    }

    fun storeOfficer(person: PersonAPI) {
        if (storedOfficer != null) {
            return
        }

        storedOfficer = person
    }

    override fun showWhenUnavailable(): Boolean {
        if (!super.showWhenUnavailable()) return false

        if (!market.faction.knowsIndustry(spec.id)) return false

        return true
    }

    override fun isAvailableToBuild(): Boolean {
        if (!market.faction.knowsIndustry(spec.id)) return false
        if (!market.isMilitary()) return false

        return super.isAvailableToBuild
    }

    override fun getUnavailableReason(): String? {
        if (!market.faction.knowsIndustry(spec.id)) return "YOU SHOULDNT SEE THIS AT ALL"
        if (!market.isMilitary()) return "Requires a military presence"

        return super.unavailableReason
    }
}