package niko_scavengableindustries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.campaign.impl.items.BlueprintProviderItem
import com.fs.starfarer.api.campaign.impl.items.IndustryBlueprintItemPlugin
import com.fs.starfarer.api.impl.SharedUnlockData
import com.fs.starfarer.api.impl.campaign.ids.Industries
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.codex.CodexDataV2
import com.fs.starfarer.api.loading.IndustrySpecAPI
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
                weight *= 0.1f
            }
            if (data.upgradeTo.isNotEmpty() && NSISettings.industrySpecs[data.upgradeTo] != null && !Global.getSector().playerFaction.knowsIndustry(data.upgradeTo)) {
                weight *= 0.1f
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

    var beingBought = false
    override fun getPrice(market: MarketAPI?, submarket: SubmarketAPI?): Int {
        var price = super.getPrice(market, submarket)

        var cap = NSISettings.maxSellPrice
        if (cap == -1) cap = Int.MAX_VALUE
        var mult = NSISettings.defaultSellMult
        if (beingBought) {
            cap = Int.MAX_VALUE
            mult = NSISettings.defaultBuyMult
        }

        price = (price * mult).roundToInt()
        return price.coerceAtMost(cap)
    }

    override fun createTooltip(
        tooltip: TooltipMakerAPI?,
        expanded: Boolean,
        transferHandler: CargoTransferHandlerAPI?,
        stackSource: Any?
    ) {
        tooltip?.codexEntryId = CodexDataV2.getIndustryEntryId(industry.id)

        val opad = 10f
        val g = Misc.getGrayColor()
        val b = Misc.getPositiveHighlightColor()

        val industryId = stack.specialDataIfSpecial.data
        val known = Global.getSector().playerFaction.knowsIndustry(industryId)

        tooltip!!.addTitle("${industry.name} - ${if (industry.hasTag(Industries.TAG_INDUSTRY)) "Industry" else "Structure"}", Misc.getBrightPlayerColor())
        val design = designType
        Misc.addDesignTypePara(tooltip, design, opad)
        tooltip.addPara(spec.desc, 10f).color = Misc.getGrayColor()

        tooltip.addSectionHeading(
            "Description",
            Alignment.MID,
            opad
        )

        tooltip.addPara(industry.desc, opad)

        val spec = NSISettings.industrySpecs[industryId]
        if (spec != null) {
            if (spec.upgradeTo.isNotEmpty()) {
                val upgradesFromSpec = Global.getSettings().getIndustrySpec(spec.upgradeTo)
                tooltip.addPara(
                    "Upgrades from %s",
                    opad,
                    Misc.getHighlightColor(),
                    "${upgradesFromSpec.name}"
                ).color = Misc.getGrayColor()
            }
            if (spec.discoveryString.isNotEmpty()) {
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
        }

        addCostLabel(tooltip, opad, transferHandler, stackSource)

        if (known) {
            tooltip.addPara("Already known", g, opad)
        } else {
            tooltip.addPara("Right-click to learn", b, opad)
        }
    }

    override fun getDesignType(): String? {
        val genSpec = NSISettings.industrySpecs[industry.id] ?: return null
        return genSpec.designType
    }

    fun getInd(): IndustrySpecAPI? = industry
}