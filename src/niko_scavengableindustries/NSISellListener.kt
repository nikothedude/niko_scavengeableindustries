package niko_scavengableindustries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCampaignEventListener
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.campaign.PlayerMarketTransaction
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.Submarkets

class NSISellListener: BaseCampaignEventListener(false) {

    override fun reportPlayerMarketTransaction(transaction: PlayerMarketTransaction?) {
        super.reportPlayerMarketTransaction(transaction)
        if (transaction == null) return
        val market = transaction.market ?: return
        val submarket = transaction.submarket ?: return
        var faction: FactionAPI?
        if (submarket.specId == Submarkets.SUBMARKET_BLACK) {
            faction = Global.getSector().getFaction(Factions.PIRATES)
        } else {
            faction = market.faction
        }
        if (faction == null) return

        var didAnything = false
        for (stack in transaction.sold.stacksCopy) {
            if (!stack.isSpecialStack) continue
            if (!stack.specialDataIfSpecial.id.contains(Ids.BLUEPRINT_ITEM)) continue

            val plugin = stack.plugin as? IndustryBPItem ?: continue
            val indSpec = plugin.getInd() ?: continue
            if (faction.knowsIndustry(indSpec.id)) continue
            faction.addKnownIndustry(indSpec.id)
            submarket.cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, stack.data, 1f)
            didAnything = true
        }

        if (didAnything) {
            submarket.cargo.sort()
        }
    }
}