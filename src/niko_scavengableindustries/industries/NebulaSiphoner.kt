package niko_scavengableindustries.industries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.terrain.NebulaTerrainPlugin
import com.fs.starfarer.api.loading.CampaignPingSpec
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import niko_scavengableindustries.utils.StringUtils.toPercent
import org.lazywizard.lazylib.ext.campaign.contains
import kotlin.math.floor

/// Siphon nebula gas when the market is in a nebula
/// Stockpile it and generate volatiles for a while after
/// New nebulas give more
class NebulaSiphoner: BaseIndustry() {

    companion object {
        const val BASE_MAX_VOLATILE_STORAGE = 1500f
        const val VOLATILE_STORAGE_PER_OUTPUT = 75f
        const val IMPROVED_COLLECTION_RATE_INCREASE = 1.25f
        const val ALPHA_CORE_STORAGE_INCR = 250f
        const val SIPHON_SPEED_SIZE_MULT = 0.25f

        const val BASE_SIPHON_RATE_PER_SECOND = 1f
        const val VOLATILES_DECAY_PER_SECOND = 0.1f

        const val BASE_OUTPUT = 1f
    }

    var storedVolatiles = 0f

    override fun apply() {
        super.apply(true)

        val size = market.size

        demand(Commodities.HEAVY_MACHINERY, size + 2)
        demand(Commodities.CREW, size - 1)

        if (!hasNebulas()) return
        val mult = getDeficitMult(
            Commodities.HEAVY_MACHINERY,
            Commodities.CREW
        )

        val storedBonus = getStoredVolatilesOutputBonus()

        supply(
            Commodities.VOLATILES,
            (BASE_OUTPUT + (storedBonus) * mult).toInt(),
            nameForModifier
        )
    }

    override fun unapply() {
        super.unapply()
    }

    val pingInterval = IntervalUtil(0.5f, 0.5f)

    override fun advance(amount: Float) {
        super.advance(amount)
        if (!isFunctional) return

        val size = market.size
        val effectiveSize = (size - 3)
        val sizeBonusMult = 1 + (SIPHON_SPEED_SIZE_MULT * effectiveSize)

        val containing = market.containingLocation ?: return
        for (terrain in containing.terrainCopy.filter { it.plugin is NebulaTerrainPlugin }) {
            val casted = terrain.plugin as NebulaTerrainPlugin
            if (!casted.containsEntity(market.primaryEntity)) continue

            var mult = 1f
            if (isImproved) mult += (IMPROVED_COLLECTION_RATE_INCREASE - 1)
            var max = BASE_MAX_VOLATILE_STORAGE
            if (aiCoreId == Commodities.ALPHA_CORE) max += ALPHA_CORE_STORAGE_INCR
            if (sizeBonusMult > 1) {
                mult += (sizeBonusMult - 1)
            }
            storedVolatiles = (storedVolatiles + (BASE_SIPHON_RATE_PER_SECOND * amount * mult)).coerceAtMost(max)

            val spec = CampaignPingSpec()
            spec.isInvert = true
            spec.duration = 2f
            spec.color = market.faction.color
            spec.range = market.primaryEntity.radius * 2f
            spec.minRange = market.primaryEntity.radius
            spec.width *= 2f
            pingInterval.advance(Misc.getDays(amount))
            if (pingInterval.intervalElapsed()) {
                Global.getSector().addPing(
                    market.primaryEntity,
                    spec
                )
            }
        }

        val currBonus = getStoredVolatilesOutputBonus()
        storedVolatiles = (storedVolatiles - (VOLATILES_DECAY_PER_SECOND * amount * (currBonus + 1))).coerceAtLeast(0f)
    }

    fun getStoredVolatilesOutputBonus(): Float {
        if (storedVolatiles == 0f) return 0f
        return (floor(storedVolatiles / VOLATILE_STORAGE_PER_OUTPUT))
    }

    override fun addPostSupplySection(
        tooltip: TooltipMakerAPI?,
        hasSupply: Boolean,
        mode: Industry.IndustryTooltipMode?
    ) {
        super.addPostSupplySection(tooltip, hasSupply, mode)

        if (tooltip == null) return

        tooltip.addPara(
            "Siphons gas from nebulae the colony passes through, which will be processed into volatiles over time. %s.",
            10f,
            Misc.getHighlightColor(),
            "The colony must pass over the nebula to siphon it"
        )

        if (hasNebulas()) {
            val bonus = getStoredVolatilesOutputBonus().toInt()
            tooltip.addPara(
                "The collector currently has %s of gas stored, translating to a bonus output of %s volatiles and a decay of %s the usual speed. Trace collection " +
                        "ensures at least %s unit of volatiles is always exported.",
                10f,
                Misc.getHighlightColor(),
                "${(storedVolatiles * 215.53f).toInt()} mols", "$bonus", "${1 + bonus}x", "one"
            )
        } else {
            tooltip.addPara(
                "There is no nebula present in the system. The collector is idle.",
                10f
            ).color = Misc.getNegativeHighlightColor()
        }
    }

    override fun canImprove(): Boolean = true

    override fun addImproveDesc(info: TooltipMakerAPI?, mode: Industry.ImprovementDescriptionMode?) {
        info?.addPara(
            "Improves gas collection rate by %s.",
            0f,
            Misc.getHighlightColor(),
            toPercent(IMPROVED_COLLECTION_RATE_INCREASE - 1)
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
                        "Improves gas storage by %s.", 0f, highlight,
                "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION, "${ALPHA_CORE_STORAGE_INCR.toInt()} mols"
            )
            tooltip.addImageWithText(opad)
            return
        }

        tooltip.addPara(
            pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                    "Improves gas storage by %s.", opad, highlight,
            "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION, "${ALPHA_CORE_STORAGE_INCR.toInt()} mols"
        )
    }

    override fun isAvailableToBuild(): Boolean {
        if (!market.faction.knowsIndustry(spec.id)) return false
        if (!hasNebulas()) return false

        return super.isAvailableToBuild()
    }

    override fun getUnavailableReason(): String? {
        if (!hasNebulas()) return "No in-system nebula"

        return super.getUnavailableReason()
    }

    override fun showWhenUnavailable(): Boolean {
        if (!market.faction.knowsIndustry(spec.id)) return false

        return super.showWhenUnavailable()
    }

    fun hasNebulas(): Boolean {
        val result = market?.containingLocation?.terrainCopy?.any { it.plugin is NebulaTerrainPlugin } == true
        return result
    }

}