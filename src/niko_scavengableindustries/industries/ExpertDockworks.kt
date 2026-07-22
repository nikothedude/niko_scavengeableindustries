package niko_scavengableindustries.industries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.FleetInflater
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode
import com.fs.starfarer.api.campaign.listeners.FleetInflationListener
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflaterParams
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.Industries
import com.fs.starfarer.api.impl.campaign.ids.Items
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import niko_scavengableindustries.utils.MarketUtils.isPrimaryHeavyIndustry
import niko_scavengableindustries.utils.MathUtils.prob
import niko_scavengableindustries.utils.StringUtils.toPercent
import org.magiclib.kotlin.isPatrol
import org.magiclib.kotlin.isWarFleet

/// If stationed on the primary heavy industry, all fleets from your faction get smods. EXPENSIVE.
// TODO FUCKING TEST THIS. GET A ALPHA GOING
class ExpertDockworks: BaseIndustry() {
    val listener = ExpertDockworksInflationListener(this)

    companion object {
        const val BASE_SMODS = 1
        const val PRISTINE_NANOFORGE_SMOD_BONUS = 1
    }

    override fun apply() {
        super.apply(true)

        if (!Global.getSector().listenerManager.hasListener(listener)) {
            Global.getSector().listenerManager.addListener(listener)
        }

        val size = market.size
        demand(Commodities.METALS, size + 2)
        demand(Commodities.RARE_METALS, size + 1)
        demand(Commodities.HEAVY_MACHINERY, size + 2)
        demand(Commodities.VOLATILES, size - 1)
    }

    fun getOurDeficitMult() = getDeficitMult(
        Commodities.METALS,
        Commodities.RARE_METALS,
        Commodities.HEAVY_MACHINERY,
        Commodities.VOLATILES
    )

    override fun unapply() {
        Global.getSector().listenerManager.removeListener(listener)
    }

    override fun isFunctional(): Boolean {
        if (!hasNanoforge() || getHeavyIndustry() == null) return false
        return super.isFunctional()
    }

    override fun addRightAfterDescriptionSection(tooltip: TooltipMakerAPI?, mode: Industry.IndustryTooltipMode?) {
        super.addRightAfterDescriptionSection(tooltip, mode)

        tooltip?.addPara(
            "Requires a %s to function.",
            10f,
            Misc.getHighlightColor(),
            "Nanoforge"
        )
    }

    override fun addPostSupplySection(
        tooltip: TooltipMakerAPI?,
        hasSupply: Boolean,
        mode: Industry.IndustryTooltipMode?
    ) {
        super.addPostSupplySection(tooltip, hasSupply, mode)

        if (tooltip == null) return
        if (mode == null) return

        tooltip.addPara(
            "Adds %s %s to all military fleets launched by this faction %s.",
            10f,
            Misc.getHighlightColor(),
            "${getSmodNum()}", "s-mods", "as long as this colony remains its primary ship supplier"
        ).setHighlightColors(
            Misc.getHighlightColor(),
            Misc.getStoryOptionColor(),
            Misc.getHighlightColor()
        )

        val deficitMult = getOurDeficitMult()
        if (getHeavyIndustry() == null) {
            tooltip.addPara(
                "${market.name} has no heavy industry. This industry is inert.",
                10f
            ).color = Misc.getNegativeHighlightColor()
        } else if (!isPrimaryHeavyIndustry()) {
            tooltip.addPara(
                "${market.name} is not the primary ship supplier. This industry is inert.",
                10f
            ).color = Misc.getNegativeHighlightColor()
        } else if (deficitMult < 1f) {
            tooltip.addPara(
                "The industry is suffering shortages and has a ${toPercent(1 - deficitMult)} chance to not apply smods.",
                10f
            ).color = Misc.getNegativeHighlightColor()
        }

        if (special != null) {
            if (special.id == Items.PRISTINE_NANOFORGE) {
                tooltip.addPara(
                    "The installed nanoforge is %s, providing an %s to fleets.",
                    10f,
                    Misc.getPositiveHighlightColor(),
                    "exceptional", "extra s-mod"
                ).setHighlightColors(
                    Misc.getPositiveHighlightColor(),
                    Misc.getStoryOptionColor()
                )
            } else if (hasNanoforge()) {
                tooltip.addPara(
                    "The installed nanoforge is %s.",
                    10f,
                    Misc.getHighlightColor(),
                    "adequate"
                )
            }
        }
        if (!hasNanoforge()) {
            tooltip.addPara(
                "Without the output of a Nanoforge, your engineers lack material. This industry is insert.",
                10f
            ).color = Misc.getNegativeHighlightColor()
        }
    }

    private fun hasNanoforge(): Boolean {
        if (special == null) return false
        val spec = Global.getSettings().getSpecialItemSpec(special.id)
        return spec.hasTag("nanoforge")
    }

    fun isPrimaryHeavyIndustry(): Boolean {
        val existingInd = getHeavyIndustry() ?: return false
        return existingInd.isPrimaryHeavyIndustry()
    }

    fun getHeavyIndustry(): Industry? = market.industries.find { it.spec.hasTag(Industries.TAG_HEAVYINDUSTRY) }

    class ExpertDockworksInflationListener(val dockworks: ExpertDockworks): FleetInflationListener {
        override fun reportFleetInflated(
            fleet: CampaignFleetAPI?,
            inflater: FleetInflater?
        ) {
            // UNHOLY CODE
            if (fleet == null || inflater == null) return
            if (!dockworks.isFunctional) return
            if (fleet.memoryWithoutUpdate["\$NSI_InflatedAlready"] == true) return
            val params = inflater.params as? DefaultFleetInflaterParams ?: return
            if (fleet.faction.id != dockworks.market.faction.id) return
            if (!dockworks.isPrimaryHeavyIndustry()) return
            val mult = dockworks.getOurDeficitMult()
            if (prob(100f - (mult * 100f))) return

            if (!dockworks.isImproved && !(fleet.isPatrol() || fleet.isWarFleet())) return
            if (inflater.removeAfterInflating() && dockworks.aiCoreId != Commodities.ALPHA_CORE) return
            if (fleet.isStationMode) return

            val sMods = dockworks.getSmodNum()
            val orig = params.averageSMods ?: 0
            params.averageSMods = (orig + sMods)

            fleet.memoryWithoutUpdate.set("\$NSI_InflatedAlready", true)
            fleet.deflate()
            val oldRemove = inflater.removeAfterInflating()
            inflater.setRemoveAfterInflating(false)
            fleet.inflateIfNeeded()
            inflater.setRemoveAfterInflating(oldRemove)
        }
    }

    override fun canImprove(): Boolean {
        return true
    }

    override fun addImproveDesc(info: TooltipMakerAPI?, mode: Industry.ImprovementDescriptionMode?) {
        info?.addPara("Expands smods to %s.", 0f, Misc.getHighlightColor(), "non-military fleets")
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
                        "Applies s-mods to ships constructed by custom production.", 0f, highlight,
                "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION
            )
            tooltip.addImageWithText(opad)
            return
        }

        tooltip.addPara(
            pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                    "Applies s-mods to ships constructed by custom production.", opad, highlight,
            "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION
        )
    }

    private fun getSmodNum(): Int {
        var base = BASE_SMODS
        if (special?.id == Items.PRISTINE_NANOFORGE) base += PRISTINE_NANOFORGE_SMOD_BONUS

        return base
    }

    override fun isAvailableToBuild(): Boolean {
        if (!market.faction.knowsIndustry(spec.id)) return false
        if (getHeavyIndustry() == null) return false

        return super.isAvailableToBuild()
    }

    override fun getUnavailableReason(): String? {
        if (getHeavyIndustry() == null) return "Requires heavy industry"

        return super.getUnavailableReason()
    }

    override fun showWhenUnavailable(): Boolean {
        if (!market.faction.knowsIndustry(spec.id)) return false

        return super.showWhenUnavailable()
    }

    override fun getPatherInterest(): Float {
        return super.patherInterest + 10f
    }

}