package niko_scavengableindustries.utils

import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import lunalib.lunaExtensions.getMarketsCopy

object MarketUtils {
    fun Industry.isPrimaryHeavyIndustry(): Boolean {
        if (market == null || market.faction == null) return false
        val shipProduction = getSupply(Commodities.SHIPS) ?: return false
        val ourProduction = shipProduction.quantity.modifiedInt
        if (ourProduction <= 0) return false
        val faction = market.faction
        for (colony in faction.getMarketsCopy()) {
            if (colony.industries.any { it.getSupply(Commodities.SHIPS) != null && it.getSupply(Commodities.SHIPS).quantity.modifiedInt > ourProduction }) {
                return false
            }
        }
        return true
    }
}