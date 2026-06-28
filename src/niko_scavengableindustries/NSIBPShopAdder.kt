package niko_scavengableindustries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCampaignEventListener
import com.fs.starfarer.api.campaign.SpecialItemData
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.Submarkets
import com.fs.starfarer.api.util.WeightedRandomPicker
import niko_scavengableindustries.utils.FactionUtils.getSellableIndustryIds
import kotlin.text.get

/** Adds augments to submarkets for purchase. */
class NSIBPShopAdder: BaseCampaignEventListener(false) {

    companion object {
        const val TIMES_TO_PICK_PER_ROLL = 1f
        const val PRISM_PICK_TIME_MULT = 3f
        val WHITELIST = listOf(
            Submarkets.SUBMARKET_OPEN,
            Submarkets.SUBMARKET_BLACK,
            Submarkets.GENERIC_MILITARY,
            "exerelin_prismMarket",
            "sotf_forgeshipmarket"
        )
    }

    override fun reportPlayerOpenedMarketAndCargoUpdated(market: MarketAPI?) {
        super.reportPlayerOpenedMarketAndCargoUpdated(market)

        if (market == null) return

        for (submarket in market.submarketsCopy) {
            if (submarket.specId !in WHITELIST) continue
            // the below is necessary since other mods, namely indevo, plug into this and set sinceSWUpdate to 0.001f
            if (market.memoryWithoutUpdate.getBoolean("\$NSI_dontUpdateBlueprints_${submarket.specId}")) continue

            addBlueprints(submarket, market)
        }
    }

    private fun addBlueprints(submarket: SubmarketAPI, market: MarketAPI) {
        val cargo = submarket.cargo
        for (stack in cargo.stacksCopy) {
            if (stack.isSpecialStack && stack.specialDataIfSpecial.id.contains(Ids.BLUEPRINT_ITEM)) {
                cargo.removeStack(stack)
            }
        }
        val faction = market.faction

        val knownBlueprints = faction.getSellableIndustryIds()
        if (submarket.specId == Submarkets.SUBMARKET_BLACK) {
            knownBlueprints += Global.getSector().getFaction(Factions.PIRATES).getSellableIndustryIds()
        }
        var picksLeft = TIMES_TO_PICK_PER_ROLL + market.size
        if (submarket.specId == "exerelin_prismMarket") {
            picksLeft *= PRISM_PICK_TIME_MULT
            knownBlueprints += Global.getSector().getFaction(Factions.MERCENARY).getSellableIndustryIds()
        }
        if (knownBlueprints.isEmpty()) return
        val picker = WeightedRandomPicker<String>()
        var totalWeight = 0f
        for (entry in knownBlueprints) {
            var weight = entry.sellWeight
            if (submarket.specId == "exerelin_prismMarket") {
                weight += (100f - weight).coerceAtLeast(0f)
            }
            picker.add(entry.id, entry.sellWeight)
            totalWeight += weight
        }
        picker.add("nothing", totalWeight * 10f)

        while (picksLeft-- > 0f) {
            val picked = picker.pick()
            if (picked == "nothing") continue

            cargo.addSpecial(SpecialItemData(Ids.BLUEPRINT_ITEM, picked), 1f)
        }
        market.memoryWithoutUpdate.set("\$NSI_dontUpdateBlueprints_${submarket.specId}", true, 30f)
    }
}