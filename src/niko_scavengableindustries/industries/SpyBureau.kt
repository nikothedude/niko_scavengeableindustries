package niko_scavengableindustries.industries

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry
import com.fs.starfarer.campaign.ai.CampaignFleetAI
import com.fs.starfarer.campaign.fleet.CampaignFleet

/// Sends fleets to go spy on other factions, doing some piracy on the way.
/// Destabilizes hostile markets, blows up trade fleets, returns with their goods (see privateer base for an example)
/// Provides sensor range boost/sensor profile reduction to your fleet in hostile systems when improved.
/// 2 industry cost.
/// Only installable on a market with a high command.
/// Only one allowed.
/// Probably causes a crisis if you use it, idk
class SpyBureau: BaseIndustry() {

    val fleets = HashSet<CampaignFleetAPI>()

    override fun apply() {
        TODO("Not yet implemented")
    }

    override fun advance(amount: Float) {
        super.advance(amount)


    }
}