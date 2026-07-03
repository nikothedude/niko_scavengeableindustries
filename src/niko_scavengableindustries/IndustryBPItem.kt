package niko_scavengableindustries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.campaign.impl.items.IndustryBlueprintItemPlugin
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry
import com.fs.starfarer.api.impl.campaign.ids.Industries
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.WeightedRandomPicker
import niko_scavengableindustries.utils.DebugUtils
import kotlin.math.roundToInt

class IndustryBPItem: IndustryBlueprintItemPlugin() {

    override fun init(stack: CargoStackAPI) {
        super.init(stack)

        if (industry != null) return

        var indId: String
        val droppedFrom = spec.params // the drop group we were dropped from
        val picker = WeightedRandomPicker<Pair<String, IndustryGenSpec>>()

        for (entry in NSISettings.industrySpecs.entries.filter { it.value.weight > 0 }) {
            val id = entry.key
            val data = entry.value

            var weight: Float = data.weight
            if (Global.getSector().playerFaction.knownIndustries.contains(id)) {
                weight *= 0.5f
            }
            if (weight > 0) {
                picker.add(Pair(id, data), weight)
            }
        }
        val industrySet = picker.pick()
        if (industrySet == null) {
            DebugUtils.log.error("null industry set when trying $droppedFrom! grabbing mining to avoid a crash")
            indId = Industries.MINING
        } else {
            indId = industrySet.first
        }
        stack.specialDataIfSpecial.data = indId
        industry = Global.getSettings().getIndustrySpec(indId)
    }

    override fun getPrice(market: MarketAPI?, submarket: SubmarketAPI?): Int {
        var price = super.getPrice(market, submarket)

        price = (price * 0.25f).roundToInt()

        return price.coerceAtMost(100000)
    }

    override fun createTooltip(
        tooltip: TooltipMakerAPI?,
        expanded: Boolean,
        transferHandler: CargoTransferHandlerAPI?,
        stackSource: Any?
    ) {
        val opad = 10f
        val g = Misc.getGrayColor()
        val b = Misc.getPositiveHighlightColor()

        val industryId = stack.specialDataIfSpecial.data
        val known = Global.getSector().playerFaction.knowsIndustry(industryId)

        tooltip!!.addTitle(industry.name, Misc.getHighlightColor())
        tooltip.addPara(industry.desc, opad)
        val spec = NSISettings.industrySpecs[industryId]
        if (spec != null) {
            tooltip.addSectionHeading(
                "Discovery",
                Alignment.MID,
                opad
            )

            tooltip.addPara(
                spec.discoveryString,
                opad
            )
        }

        addCostLabel(tooltip, opad, transferHandler, stackSource)

        if (known) {
            tooltip.addPara("Already known", g, opad)
        } else {
            tooltip.addPara("Right-click to learn", b, opad)
        }
    }

}