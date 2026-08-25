package niko_scavengableindustries.industries

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI
import com.fs.starfarer.api.impl.campaign.intel.group.SindrianDiktatPunitiveExpedition
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc

class NSICryosanctumRaidFGI(params: GenericRaidParams) : GenericRaidFGI(params) {

    private val interval: IntervalUtil = IntervalUtil(0.1f, 0.3f)

    override fun getFleetCreationFactionOverride(size: Int): String? {
        return Factions.PIRATES
    }

    override fun advance(amount: Float) {
        super.advance(amount)

        val days = Misc.getDays(amount)
        interval.advance(days)

        if (interval.intervalElapsed()) {
            if (isCurrent(PAYLOAD_ACTION)) {
                val reason = "NSICryosanctumEx"
                for (curr in getFleets()) {
                    Misc.setFlagWithReason(
                        curr.memoryWithoutUpdate, MemFlags.MEMORY_KEY_MAKE_HOSTILE,
                        reason, true, 1f
                    )
                }
            }
        }
    }

    override fun preConfigureFleet(size: Int, m: FleetCreatorMission) {
        m.setFleetTypeMedium(FleetTypes.TASK_FORCE) // default would be "Patrol", don't want that
    }

    override fun configureFleet(size: Int, m: FleetCreatorMission) {
        m.triggerSetFleetFlag("\$NSICryosanctumExFleet")
        m.triggerSetFleetDoctrineOther(1, 5)
    }

    override fun doCustomRaidAction(fleet: CampaignFleetAPI?, market: MarketAPI?, raidStr: Float) {
        super.doCustomRaidAction(fleet, market, raidStr)

        market?.getIndustry("NSI_cryosanctum")?.setDisrupted(365f, true)
    }

    override fun hasCustomRaidAction(): Boolean {
        return true
    }

}