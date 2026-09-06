package niko_scavengableindustries.industries.OutpostDefense

import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator.TaskInterval
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import niko_scavengableindustries.utils.MathUtils

class OutpostDefenseAssignmentAI(fleet: CampaignFleetAPI?, route: RouteData?, val source: SectorEntityToken) : RouteFleetAssignmentAI(fleet, route), FleetActionTextProvider {

    companion object {
        const val PREP_STAGE: String = "a"
        const val TRAVEL_TO_STAGE: String = "b"
        const val PATROL_STAGE: String = "c"
        const val RETURN_STAGE: String = "d"
        const val STAND_DOWN_STAGE: String = "e"

        const val DIST_FOR_ATTACK = 800f
    }

    override fun giveInitialAssignments() {
        //super.giveInitialAssignments();

        val current = route.getCurrent()
        val source = route.getMarket().primaryEntity

        val intervals: Array<TaskInterval> = arrayOf(
            TaskInterval.days(org.lazywizard.lazylib.MathUtils.getRandomNumberInRange(90f, 93f))
        )

        RouteLocationCalculator.computeIntervalsAndSetLocation(
            fleet, current.elapsed, current.daysMax,
            false, intervals,
            source, source
        )

        fleet.clearAssignments()

        if (intervals[0].value > 0) {
            fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, source, intervals[0].value, PATROL_STAGE)
        }
        fleet.addAssignment(
            FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, source, 1000f,
            STAND_DOWN_STAGE, goNextScript(current)
        )

        fleet.ai.actionTextProvider = this
    }

    override fun getActionText(fleet: CampaignFleetAPI): String? {
//		if (Misc.getDistance(Global.getSector().getPlayerFleet(), fleet) < fleet.getRadius()) {
//			System.out.println("ewfwefwe");
//		}
        val curr = fleet.currentAssignment ?: return null

        val stage = curr.actionText
        val target = curr.target

        var name = ""
        if (target != null) {
            name = target.name
            if (target is CustomCampaignEntityAPI) {
                val cce = target
                if (name == cce.customEntitySpec.defaultName) {
                    //name = name.toLowerCase();
                    name = cce.customEntitySpec.nameInText
                }
            }
        }

        val pirate = fleet.memoryWithoutUpdate.getBoolean(MemFlags.MEMORY_KEY_PIRATE)

        if (pirate) {
            if (PREP_STAGE == stage) {
                return "preparing for patrol duty"
            } else if (TRAVEL_TO_STAGE == stage && target != null && !target.isSystemCenter && !target.isInHyperspace) {
                return "traveling to " + name
            } else if (TRAVEL_TO_STAGE == stage) {
                return "traveling"
            } else if (PATROL_STAGE == stage && target != null) {
                if (target.hasTag(Tags.OBJECTIVE)) {
                    return "guarding " + name
                } else if (target.hasTag(Tags.JUMP_POINT)) {
                    return "guarding " + name
                } else if (target.market != null) {
                    return "defending " + name
                } else {
                    return "patrolling"
                }
            } else if (RETURN_STAGE == stage && target != null && !target.isSystemCenter) {
                return "returning to " + name
            } else if (STAND_DOWN_STAGE == stage) {
                return "standing down"
            }
        } else {
            if (PREP_STAGE == stage) {
                return "preparing for patrol duty"
            } else if (TRAVEL_TO_STAGE == stage && target != null && !target.isSystemCenter && !target.isInHyperspace) {
                return "traveling to " + name
            } else if (TRAVEL_TO_STAGE == stage) {
                return "traveling"
            } else if (PATROL_STAGE == stage && target != null) {
                if (target.hasTag(Tags.OBJECTIVE)) {
                    return "guarding " + name
                } else if (target.hasTag(Tags.JUMP_POINT)) {
                    return "guarding " + name
                } else if (target.market != null) {
                    return "performing planetary defense duties"
                } else {
                    return "patrolling"
                }
            } else if (RETURN_STAGE == stage && target != null && !target.isSystemCenter) {
                return "returning to " + name
            } else if (STAND_DOWN_STAGE == stage) {
                return "standing down from patrol duty"
            }
        }


        //"traveling to " + target.getName()
        //return "patrolling";
        return null
    }

    val interval = IntervalUtil(0.1f, 0.11f)
    override fun advance(amount: Float) {
        super.advance(amount)

        val days = Misc.getDays(amount)
        interval.advance(days)
        if (interval.intervalElapsed()) {
            for (iterFleet in fleet.starSystem.fleets - fleet) {
                if (!fleet.isHostileTo(iterFleet)) continue
                val dist = org.lazywizard.lazylib.MathUtils.getDistance(iterFleet, source)
                if (dist <= DIST_FOR_ATTACK) {
                    fleet.memoryWithoutUpdate.unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS)
                    break
                } else {
                    fleet.memoryWithoutUpdate.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true)
                }
            }
        }
    }
}