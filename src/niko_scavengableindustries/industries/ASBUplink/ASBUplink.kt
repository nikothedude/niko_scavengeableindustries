package niko_scavengableindustries.industries.ASBUplink

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignTerrainAPI
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode
import com.fs.starfarer.api.campaign.econ.Industry.IndustryTooltipMode
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry
import com.fs.starfarer.api.impl.campaign.econ.impl.GroundDefenses
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.Industries
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import niko_scavengableindustries.industries.BlastCoreMining.Companion.ALPHA_INCR
import niko_scavengableindustries.industries.BlastCoreMining.Companion.IMPROVED_INCR
import niko_scavengableindustries.utils.StringUtils.toPercent

/// Allows ASBs to fire in-combat and in-campaign (need to make code for both... shouldnt be hard for campaign. Check aegis missiles...)
class ASBUplink: BaseIndustry() {

    companion object {
        const val GROUND_DEFENSE_MULT = 1.2f
    }

    var terrain: ASBUplinkTerrain? = null

    override fun apply() {
        super.apply(true)
        val size = market.size
        demand(Commodities.HAND_WEAPONS, size + 1)
        demand(Commodities.MARINES, size + 1)
        demand(Commodities.SUPPLIES, size)

        if (!isFunctional) return

        ensureTerrainExists()

        val shortages = getDeficitMult(
            Commodities.HAND_WEAPONS,
            Commodities.MARINES,
            Commodities.SUPPLIES
        )
        val bonusText = if (shortages < 1) " (Shortages)" else ""

        market.stats.dynamic.getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(id, GROUND_DEFENSE_MULT, "$nameForModifier$bonusText")
    }

    var reapplying = false
    override fun reapply() {
        reapplying = true
        super.reapply()
        reapplying = false
    }

    override fun unapply() {
        super.unapply()

        if (!reapplying) {
            if (terrain != null) {
                market.containingLocation?.removeEntity(terrain?.entity)
                terrain = null
            }
        }

        market.stats.dynamic.getMod(Stats.GROUND_DEFENSES_MOD).unmodify(id)
    }

    private fun ensureTerrainExists() {
        if (terrain == null) {
            val containing = market.containingLocation ?: return
            val entity: CampaignTerrainAPI = containing.addTerrain(
                "NSI_asbTerrain",
                ASBUplinkTerrain.ASBUplinkTerrainParams(
                    100f,
                    100f,
                    market.primaryEntity,
                    "aaaa",
                    this
                )
            ) as CampaignTerrainAPI
            entity.setLocation(market.primaryEntity.location.x, market.primaryEntity.location.y)
            terrain = entity.plugin as ASBUplinkTerrain
        }
    }

    override fun advance(amount: Float) {
        super.advance(amount)

        terrain?.entity?.setLocation(market.primaryEntity.location.x, market.primaryEntity.location.y)
    }

    fun getShortagesForTerrain(): Float {
        return getDeficitMult(
            Commodities.HAND_WEAPONS,
            Commodities.SUPPLIES
        )
    }

    override fun addPostDemandSection(tooltip: TooltipMakerAPI?, hasDemand: Boolean, mode: IndustryTooltipMode?) {
        //if (mode == IndustryTooltipMode.NORMAL && isFunctional()) {

        if (tooltip == null) return

        tooltip.addPara(
            "Establishes a %s around the colony, wherein any %s will be %s, dealing CR and hull damage over time.",
            10f,
            Misc.getHighlightColor(),
            "no-fly zone", "hostile fleets", "bombarded by flak"
        ).setHighlightColors(
            Misc.getHighlightColor(),
            Misc.getNegativeHighlightColor(),
            Misc.getPositiveHighlightColor()
        )

        tooltip.addPara(
            "Fleets with %s and/or %s will be harder to hit.",
            10f,
            Misc.getHighlightColor(),
            "low sensor profile", "high burn"
        )

        tooltip.addPara(
            "Provides %s in battles, albeit %s and %s.",
            10f,
            Misc.getHighlightColor(),
            "fire support", "highly inaccurate", "relativey low-damage"
        ).setHighlightColors(
            Misc.getPositiveHighlightColor(),
            Misc.getNegativeHighlightColor(),
            Misc.getNegativeHighlightColor()
        )

        if (ASBUplinkTerrain.getBatteries(market) == null) {
            tooltip.addPara(
                "There are no ground defenses to augment on ${market.name}. The flak cannons have no targeting solution.",
                10f
            ).color = Misc.getNegativeHighlightColor()
        } else {
            tooltip.addPara(
                "Flak cannon performance is dependant on the tier of ground defenses currently installed. %s will cause the flak cannons to do more damage.",
                10f,
                Misc.getPositiveHighlightColor(),
                "Higher tier defenses"
            )
        }

        if (mode != IndustryTooltipMode.NORMAL || isFunctional) {
            addStabilityPostDemandSection(tooltip, hasDemand, mode)

            val bonus = GROUND_DEFENSE_MULT - 1f
            addGroundDefensesImpactSection(
                tooltip,
                bonus,
                Commodities.SUPPLIES,
                Commodities.MARINES,
                Commodities.HAND_WEAPONS
            )
        }
    }

    override fun canImprove(): Boolean {
        return true
    }

    override fun addImproveDesc(info: TooltipMakerAPI?, mode: Industry.ImprovementDescriptionMode?) {
        info?.addPara("Increases barrage range by %s.", 0f, Misc.getHighlightColor(), toPercent(ASBUplinkTerrain.IMPROVED_RANGE_MULT - 1f))
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
                        "Increases flak damage by %s.", opad, highlight,
                "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION, toPercent(ASBUplinkTerrain.ALPHA_DAMAGE_MULT - 1f)
            )
            tooltip.addImageWithText(opad)
            return
        }

        tooltip.addPara(
            pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                    "Increases flak damage by %s.", opad, highlight,
            "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION, toPercent(ASBUplinkTerrain.ALPHA_DAMAGE_MULT - 1f)
        )
    }

    override fun isFunctional(): Boolean {
        return ASBUplinkTerrain.getBatteries(market) != null && super.isFunctional()
    }

    override fun isAvailableToBuild(): Boolean {
        if (!market.faction.knowsIndustry(spec.id)) return false
        if (ASBUplinkTerrain.getBatteries(market) == null) return false

        return super.isAvailableToBuild()
    }

    override fun getUnavailableReason(): String? {
        if (ASBUplinkTerrain.getBatteries(market) == null) return "Requires ground defenses"

        return super.getUnavailableReason()
    }

    override fun showWhenUnavailable(): Boolean {
        if (!market.faction.knowsIndustry(spec.id)) return false

        return super.showWhenUnavailable()
    }
}