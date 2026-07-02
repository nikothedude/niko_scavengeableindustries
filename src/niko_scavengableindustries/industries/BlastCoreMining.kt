package niko_scavengableindustries.industries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.Conditions
import com.fs.starfarer.api.impl.campaign.ids.Industries
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import niko_scavengableindustries.industries.SpyBureau.SpyBureau.Companion.BONUS_FLEETS_IMPROVED
import niko_scavengableindustries.industries.SpyBureau.SpyBureau.Companion.SENSOR_MULT
import niko_scavengableindustries.utils.MathUtils.prob
import niko_scavengableindustries.utils.StringUtils.toPercent
import java.awt.Color
import kotlin.collections.iterator
import kotlin.math.min

/// Mining industry that produces a ridiculous amount of ore and volatiles and shit but slowly reduces the deposits on the planet to nothing.
/// OR companion to a mining industry?
class BlastCoreMining: BaseIndustry() {

    companion object {
        val PROGRESSION_LIST = mapOf(
            Pair(Conditions.ORE_ULTRARICH, Conditions.ORE_RICH),
            Pair(Conditions.ORE_RICH, Conditions.ORE_ABUNDANT),
            Pair(Conditions.ORE_ABUNDANT, Conditions.ORE_MODERATE),
            Pair(Conditions.ORE_MODERATE, Conditions.ORE_SPARSE),
            Pair(Conditions.ORE_SPARSE, "REMOVE"),

            Pair(Conditions.RARE_ORE_ULTRARICH, Conditions.RARE_ORE_RICH),
            Pair(Conditions.RARE_ORE_RICH, Conditions.RARE_ORE_ABUNDANT),
            Pair(Conditions.RARE_ORE_ABUNDANT, Conditions.RARE_ORE_MODERATE),
            Pair(Conditions.RARE_ORE_MODERATE, Conditions.RARE_ORE_SPARSE),
            Pair(Conditions.RARE_ORE_SPARSE, "REMOVE"),

            Pair(Conditions.ORGANICS_PLENTIFUL, Conditions.ORGANICS_ABUNDANT),
            Pair(Conditions.ORGANICS_ABUNDANT, Conditions.ORGANICS_COMMON),
            Pair(Conditions.ORGANICS_COMMON, Conditions.ORGANICS_TRACE),
            Pair(Conditions.ORGANICS_TRACE, "REMOVE"),

            Pair(Conditions.VOLATILES_PLENTIFUL, Conditions.VOLATILES_ABUNDANT),
            Pair(Conditions.ORGANICS_ABUNDANT, Conditions.VOLATILES_DIFFUSE),
            Pair(Conditions.VOLATILES_DIFFUSE, Conditions.VOLATILES_TRACE),
            Pair(Conditions.VOLATILES_TRACE, "REMOVE"),
        )

        const val OUTPUT_MULT = 1.5f
        const val IMPROVED_INCR = 0.25f
        const val ALPHA_INCR = 0.25f
        const val INCOME_MULT = 3f
        const val TECTONIC_CHANCE = 0.5f

        const val DAYS_PER_DOWNGRADE = 60f

        const val IGNORE_CHANCE = 5f
    }

    override fun apply() {
        super.apply(true)

        val size = market.size
        demand(Commodities.HAND_WEAPONS, size - 2)
        demand(Commodities.CREW, size - 2)
        val mult = getDeficitMult(
            Commodities.HAND_WEAPONS,
            Commodities.CREW
        )
        val bonusText = if (mult < 1f) " (Shortages)" else ""
        var outputMult = OUTPUT_MULT
        if (aiCoreId == Commodities.ALPHA_CORE) outputMult += ALPHA_INCR
        if (isImproved) outputMult += IMPROVED_INCR

        val allMining = getMining()
        for (ind in allMining) {
            for (supply in ind.allSupply) {
                supply.quantity.modifyMult(
                    getModId(0),
                    (outputMult + (outputMult - (mult * outputMult))),
                    "$nameForModifier$bonusText"
                )
            }
        }
    }

    override fun unapply() {
        super.unapply()

        val allMining = getMining()
        for (ind in allMining) {
            for (supply in ind.allSupply) {
                supply.quantity.unmodify(getModId(0))
            }
        }
    }

    val downgradeInterval = IntervalUtil(DAYS_PER_DOWNGRADE, DAYS_PER_DOWNGRADE + 1f)
    override fun advance(amount: Float) {
        super.advance(amount)

        val days = Misc.getDays(amount)
        downgradeInterval.advance(days)
        if (downgradeInterval.intervalElapsed()) {
            val result = attemptDowngrade()
            if (result.isNotEmpty() && market.isPlayerOwned) {
                val hlList = ArrayList<String>()
                val hlColorList = ArrayList<Color>()
                var string = "Blast-Core Mining results on ${market.name}: "
                hlList += "${market.name}"
                hlColorList += market.faction.baseUIColor
                for (entry in result) {
                    string += "\n" +
                            "   -${entry.key} -> ${entry.value}"
                    hlList += entry.key
                    hlList += entry.value
                    hlColorList += Misc.getHighlightColor()
                    hlColorList += Misc.getNegativeHighlightColor()
                }
                val intel = MessageIntel(
                    string,
                    Misc.getTextColor(),
                    hlList.toTypedArray(),
                    *hlColorList.toTypedArray(),
                )
                intel.icon = "graphics/icons/intel/damage.png"
                intel.sound = "ui_intel_major_posted"
                Global.getSector().intelManager.addIntel(intel)

                for (mining in getMining()) {
                    mining.setDisrupted(1f, false)
                    mining.reapply()
                    mining.setDisrupted(0f)
                    mining.reapply()
                }
            }
            tryConditions()
        }
    }

    private fun tryConditions() {
        if (prob(TECTONIC_CHANCE) && !(market.hasCondition(Conditions.TECTONIC_ACTIVITY) || market.hasCondition(Conditions.EXTREME_TECTONIC_ACTIVITY))) {
            val intel = MessageIntel(
                "Added Tectonic Activity to ${market.name}",
                Misc.getTextColor(),
                arrayOf("Tectonic Activity"),
                Misc.getHighlightColor()
            )
            market.addCondition(Conditions.TECTONIC_ACTIVITY)
            intel.icon = "graphics/icons/intel/damage.png"
            intel.sound = "ui_intel_major_posted"
            Global.getSector().intelManager.addIntel(intel)
        }
    }

    private fun attemptDowngrade(): HashMap<String, String> {
        val result = HashMap<String, String>()
        for (condition in market.conditions.toSet()) {
            val outcome = PROGRESSION_LIST[condition.spec.id] ?: continue
            if (prob(IGNORE_CHANCE)) {
                continue
            }
            if (outcome == "REMOVE") {
                result[condition.spec.name] = "Removed"
                market.removeCondition(condition.spec.id)
                continue
            }
            result[condition.spec.name] = Global.getSettings().getMarketConditionSpec(outcome).name
            swapConditions(condition.spec.id, outcome)
        }
        return result
    }

    private fun swapConditions(id: String, newId: String) {
        market.removeCondition(id)
        market.addCondition(newId)
        market.getCondition(newId).isSurveyed = true
    }

    fun getMining(): List<Industry> {
        return market.industries.filter { it.spec.hasTag(Industries.MINING) }
    }

    override fun addPostDemandSection(
        tooltip: TooltipMakerAPI?,
        hasDemand: Boolean,
        mode: Industry.IndustryTooltipMode?
    ) {
        super.addPostDemandSection(tooltip, hasDemand, mode)

        if (tooltip == null) return

        tooltip.addPara(
            "Boosts ore output of %s by %s, but %s over time.",
            10f,
            Misc.getHighlightColor(),
            "mining industries", "${OUTPUT_MULT}x", "removes ore deposits"
        )
        tooltip.addPara(
            "Has a small chance of provoking %s over time.",
            10f,
            Misc.getNegativeHighlightColor(),
            "tectonic activity"
        )

        if (getMining().isEmpty()) {
            tooltip.addPara(
                "There is no mining present to boost. This industry is inert.",
                10f
            ).color = Misc.getNegativeHighlightColor()
        }
    }

    override fun isFunctional(): Boolean {
        return getMining().isNotEmpty() && super.isFunctional()
    }

    override fun isAvailableToBuild(): Boolean {
        if (!market.faction.knowsIndustry(spec.id)) return false
        if (getMining().isEmpty()) return false

        return super.isAvailableToBuild()
    }

    override fun getUnavailableReason(): String? {
        if (getMining().isEmpty()) return "Requires mining"

        return super.getUnavailableReason()
    }

    override fun showWhenUnavailable(): Boolean {
        if (!market.faction.knowsIndustry(spec.id)) return false

        return super.showWhenUnavailable()
    }

    override fun getPatherInterest(): Float {
        return super.patherInterest + 10f
    }

    override fun canImprove(): Boolean {
        return true
    }

    override fun addImproveDesc(info: TooltipMakerAPI?, mode: Industry.ImprovementDescriptionMode?) {
        info?.addPara("Increases ore output by a further %s.", 0f, Misc.getHighlightColor(), toPercent(IMPROVED_INCR))
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
                        "Increases ore output by a further %s.", 0f, highlight,
                "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION, toPercent(ALPHA_INCR)
            )
            tooltip.addImageWithText(opad)
            return
        }

        tooltip.addPara(
            pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                    "Increases ore output by a further %s.", opad, highlight,
            "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION, toPercent(ALPHA_INCR)
        )
    }

}