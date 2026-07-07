package niko_scavengableindustries.industries.SpyBureau

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.Script
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.ai.FleetAIFlags
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.listeners.FleetEventListener
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest
import com.fs.starfarer.api.impl.campaign.ids.Abilities
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseAssignmentAI
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.ai.CampaignFleetAI
import com.fs.starfarer.campaign.ai.ModularFleetAI
import niko_scavengableindustries.NSFIBaseScript
import org.codehaus.janino.Mod
import org.lazywizard.lazylib.MathUtils
import org.magiclib.kotlin.getMarketsInLocation
import org.magiclib.kotlin.getStationFleet
import org.magiclib.kotlin.getVisibleFleets
import org.magiclib.kotlin.isTrader

class SpyFleetAssignmentAI(
    val fleet: CampaignFleetAPI,
    val origFaction: FactionAPI
): NSFIBaseScript(), FleetEventListener {

    companion object {
        fun giveInitialAssignments(
            fleet: CampaignFleetAPI,
            target: StarSystemAPI,
            script: SpyFleetAssignmentAI
        ) {
            val hyper = Global.getSector().hyperspace
            val dest = Misc.getPointAtRadius(target.location, 1500f)
            val token = hyper.createToken(dest)

            class ReachedDestScript(): Script {
                override fun run() {
                    fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED] = false
                    script.state = State.OPERATING
                }
            }

            fleet.clearAssignments()
            fleet.addAssignmentAtStart(
                FleetAssignment.GO_TO_LOCATION,
                token,
                Float.MAX_VALUE,
                ReachedDestScript()
            )

            fleet.addAssignment(
                FleetAssignment.PATROL_SYSTEM,
                target.star,
                Float.MAX_VALUE, // we will override this via our duration variable
                "maneuvering"
            )
        }

        const val SUPERIORITY_RATIO_TO_ATTACK_TRADERS = 1.3f
        const val SUPERIORITY_RATIO_TO_TRY_DESTABILIZING = 1.1f
        const val DIST_TO_MARKET_TO_CONSIDER_DESTABILIZING = 3000f
        const val DIST_TO_TRADER_TO_CONSIDER_ATTACKING = 1000f
        const val STABILITY_MIN = 3f
        const val FP_ABORT_RATIO = 0.7f
    }

    var state: State = State.GOING_TO_TARGET
    enum class State {
        GOING_TO_TARGET,
        OPERATING,
        ACTING,
        RETURNING;
    }

    val startingFP = fleet.fleetPoints

    override fun startImpl() {
        fleet.addScript(this)
        fleet.addEventListener(this)
    }

    override fun stopImpl() {
        fleet.removeScript(this)
        fleet.removeEventListener(this)
    }

    override fun runWhilePaused(): Boolean = false

    val durationInterval = IntervalUtil(130f, 131f)
    val checkInterval = IntervalUtil(0.1f, 0.11f)
    override fun advance(amount: Float) {
        durationInterval.advance(Misc.getDays(amount))

        if (durationInterval.intervalElapsed()) {
            returnToBase()
            return
        }

        checkInterval.advance(Misc.getDays(amount))
        if (checkInterval.intervalElapsed()) {
            checkOpportunities()
        }

        if ((fleet.fleetPoints.toFloat() / startingFP.toFloat()) <= FP_ABORT_RATIO) {
            return returnToBase()
        }

        val assignment = fleet.currentAssignment ?: return returnToBase()
        if (assignment != null && assignment.assignment == FleetAssignment.INTERCEPT) {
            if (!assignment.target.isAlive) {
                returnToBase()
            }
        }
    }

    private fun returnToBase() {
        if (fleet.currentAssignment != null && state == State.RETURNING) return

        val originalId = fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_SOURCE_MARKET] as? String ?: return fleet.despawn()
        val original = Global.getSector().economy.getMarket(originalId) ?: return fleet.despawn()

        state = State.RETURNING

        fleet.clearAssignments()
        fleet.addAssignmentAtStart(
            FleetAssignment.GO_TO_LOCATION_AND_DESPAWN,
            original.primaryEntity,
            Float.MAX_VALUE,
            "travelling",
            null, // handled thru despawn listener(?)
        )
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED] = true

        delete()
    }

    private fun checkOpportunities() {
        if (maskOff) return

        val containing = fleet.containingLocation

        for (iterFleet in fleet.getVisibleFleets(false)) {
            if (!iterFleet.isTrader()) continue
            if (!iterFleet.faction.isHostileTo(origFaction)) continue
            val dist = MathUtils.getDistance(fleet, iterFleet)
            if (dist > DIST_TO_TRADER_TO_CONSIDER_ATTACKING) continue

            // we want to attack them. now we check to see if we can take them and their allies

            val allies = getPossibleParticipants(false, iterFleet, null)
            val enemies = getPossibleParticipants(true, iterFleet, null)

            val ourStrength = allies.sumOf { it.fleetPoints }.toFloat()
            val theirStrength = enemies.sumOf { it.fleetPoints }.toFloat()

            if (theirStrength == 0f || (ourStrength / theirStrength) >= SUPERIORITY_RATIO_TO_ATTACK_TRADERS) {
                attackTrader(iterFleet)
                return
            }
        }

        for (market in containing.getMarketsInLocation()) {
            if (!SpyBureau.marketIsValidTarget(market, origFaction)) continue

            val dist = org.lazywizard.lazylib.MathUtils.getDistance(fleet, market.primaryEntity)
            if (dist > DIST_TO_MARKET_TO_CONSIDER_DESTABILIZING) continue

            val allies = getPossibleParticipants(false, null, market)
            val enemies = getPossibleParticipants(true, null, market)

            val ourStrength = allies.sumOf { it.fleetPoints }.toFloat()
            val theirStrength = enemies.sumOf { it.fleetPoints }.toFloat()

            if (theirStrength == 0f || (ourStrength / theirStrength) >= SUPERIORITY_RATIO_TO_TRY_DESTABILIZING) {
                attackMarket(market)
                return
            }
        }
    }

    var maskOff = false
    fun maskOff() {
        fleet.setFaction(origFaction.id, true)
        fleet.name = "Covert Operations"
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_FORCE_TRANSPONDER_OFF] = true
        fleet.memoryWithoutUpdate.unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS)
        maskOff = true
    }

    private fun attackMarket(market: MarketAPI) {
        state = State.ACTING

        maskOff()

        fleet.clearAssignments()
        class AfterRaidScript: Script {
            override fun run() {
                val stability = market.stability.modified
                if (stability >= STABILITY_MIN) {
                    RecentUnrest.get(market).add(2, "Destabilized by covert action")
                }
                returnToBase()
                market.primaryEntity.addScript(MarketCMD.BombardmentAnimation(12, market.primaryEntity))
            }
        }
        fleet.addAssignmentAtStart(
            FleetAssignment.DELIVER_CREW,
            market.primaryEntity,
            30f,
            "moving in to destabilize ${market.name}",
            AfterRaidScript()
        )

        Misc.setFlagWithReason(
            fleet.memoryWithoutUpdate,
            MemFlags.FLEET_BUSY, BaseAssignmentAI.TEMP_BUSY_REASON, true, (1.5f + Math.random().toFloat())
        )
    }

    fun attackTrader(target: CampaignFleetAPI) {
        state = State.ACTING

        maskOff()

        class EndScript: Script {
            override fun run() {
                returnToBase()
            }
        }

        fleet.clearAssignments()
        fleet.addAssignmentAtStart(
            FleetAssignment.INTERCEPT,
            target,
            15f,
            EndScript()
        )
        fleet.memoryWithoutUpdate[FleetAIFlags.LAST_SEEN_TARGET_LOC] = target.location
        fleet.memoryWithoutUpdate[FleetAIFlags.LAST_SEEN_TARGET_HEADING] = target.facing

        val eburn = fleet.getAbility(Abilities.EMERGENCY_BURN)
        if (eburn != null && !eburn.isActiveOrInProgress) {
            eburn.activate()
        }
    }

    fun getPossibleParticipants(hostile: Boolean, target: CampaignFleetAPI?, market: MarketAPI?): MutableSet<CampaignFleetAPI> {
        val participants = HashSet<CampaignFleetAPI>()

        val visible = fleet.getVisibleFleets(true).toMutableSet()
        if (market != null && market.getStationFleet() != null) {
            val stationFleet = market.getStationFleet()
            stationFleet.fleetData.setSyncNeeded()
            stationFleet.fleetData.syncIfNeeded()
            visible += market.getStationFleet()
        }
        for (iterFleet in fleet.getVisibleFleets(true)) {
            if (!iterFleet.isStationMode && fleet.getVisibilityLevelTo(iterFleet).ordinal <= SectorEntityToken.VisibilityLevel.SENSOR_CONTACT.ordinal) continue
            if (iterFleet.memoryWithoutUpdate[MemFlags.FLEET_IGNORES_OTHER_FLEETS] == true || iterFleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED] == true) continue
            if (!hostile && iterFleet == fleet) continue
            if (!hostile && iterFleet == target) continue

            if (hostile && !iterFleet.faction.isHostileTo(origFaction)) continue
            else if (!hostile && (target == null || (!iterFleet.isHostileTo(target)) || iterFleet.faction.isHostileTo(origFaction))) continue

            participants += iterFleet
        }

        if (!hostile) participants += fleet
        return participants
    }

    override fun reportFleetDespawnedToListener(
        fleet: CampaignFleetAPI?,
        reason: CampaignEventListener.FleetDespawnReason?,
        param: Any?
    ) {
        if (fleet == this.fleet) delete()
    }

    override fun reportBattleOccurred(
        fleet: CampaignFleetAPI?,
        primaryWinner: CampaignFleetAPI?,
        battle: BattleAPI?
    ) {
        if (fleet != this.fleet) return
        if (battle == null) return
        if (fleet.currentAssignment != null && battle.bothSides.contains(fleet.currentAssignment.target)) {
            returnToBase()
        }

        /*if (fleet != this.fleet) return
        if (battle == null) return
        if (primaryWinner == null) return
        val other = battle.getOtherSideFor(this.fleet)
        val traders = other.filter { it.isTrader() }
        if (traders.isEmpty()) return

        if (primaryWinner == this.fleet) {
            for (trader in traders) {
                val lost = Misc.getSnapshotMembersLost(trader)
                if (lost.isEmpty()) continue
                val cargoLost = lost.sumOf { it.cargoCapacity as Int }

            }
            traders.first()
        } else {
            returnToBase()
        }*/
    }
}