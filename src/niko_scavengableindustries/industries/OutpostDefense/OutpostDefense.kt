package niko_scavengableindustries.industries.OutpostDefense

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.Industry.IndustryTooltipMode
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase
import com.fs.starfarer.api.impl.campaign.fleets.PatrolAssignmentAIV4
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage
import com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import niko_scavengableindustries.ReflectionUtilsV2.get
import niko_scavengableindustries.ReflectionUtilsV2.set
import niko_scavengableindustries.utils.MathUtils.trimHangingZero
import org.magiclib.kotlin.setFlagWithReason
import sound.int
import java.util.*

class OutpostDefense: MilitaryBase(), MarketImmigrationModifier {

    companion object {
        const val DEFENSE_BONUS = 1f
        const val MAX_SIZE = 5
        const val OFFICER_PROB = 0.2f
        const val RESPAWN_MULT = 0.5f

        //const val ACCESS_MALUS = -0.1f

        const val BASE_DELAY_INCR = 10f
        const val BASE_DELAY_MULT = 3f
    }

    val checked = HashSet<RaidIntel>()

    val interval = IntervalUtil(0.1f, 0.11f)

    override fun advance(amount: Float) {
        super.advance(amount)

        if (!isFunctional) return

        interval.advance(Misc.getDays(amount))
        if (!interval.intervalElapsed()) return

        checked.removeAll { it.isEnding || it.isEnded }

        val raids = ArrayList<RaidIntel>()
        for (raid in Global.getSector().intelManager.intel - checked) {
            if (raid is RaidIntel && raid.system == market.starSystem) raids += raid
        }

        for (raid in raids) {
            if (raid.organizeStage == null) continue
            val max = get("maxDays", raid.organizeStage, Float::class.java, clazz = BaseRaidStage::class.java) as Float
            set("maxDays", raid.organizeStage, (max + BASE_DELAY_INCR) * BASE_DELAY_MULT, Float::class.java, clazz = BaseRaidStage::class.java)
            checked += raid
        }
    }

    override fun apply() {
        market.addTransientImmigrationModifier(this)
        val size = market.size

        super.apply(true)
        if (!isFunctional) return

        var extraDemand = 1

        var light = if (isImproved) 1 else 0
        var medium = 0
        var heavy = 5

//		if (market.getId().equals("jangala")) {
//			System.out.println("wefwefwe");
//		}

//		light += 5;
//		medium += 3;
//		heavy += 2;

//		float spawnRateMultStability = getStabilitySpawnRateMult();
//		if (spawnRateMultStability != 1) {
//			market.getStats().getDynamic().getStat(Stats.COMBAT_FLEET_SPAWN_RATE_MULT).modifyMult(getModId(), spawnRateMultStability);
//		}
        market.stats.dynamic.getMod(Stats.PATROL_NUM_LIGHT_MOD).modifyFlat(modId, light.toFloat())
        market.stats.dynamic.getMod(Stats.PATROL_NUM_MEDIUM_MOD).modifyFlat(modId, medium.toFloat())
        market.stats.dynamic.getMod(Stats.PATROL_NUM_HEAVY_MOD).modifyFlat(modId, heavy.toFloat())
        market.stats.dynamic.getStat(Stats.COMBAT_FLEET_SPAWN_RATE_MULT).modifyFlat(modId, RESPAWN_MULT)

        demand(Commodities.SUPPLIES, size - 1 + extraDemand)
        demand(Commodities.FUEL, size - 1 + extraDemand)
        demand(Commodities.SHIPS, size - 2 + extraDemand)
        demand(Commodities.HAND_WEAPONS, size - 1 + extraDemand)

        supply(Commodities.CREW, size)
        supply(Commodities.MARINES, size)

        modifyStabilityWithBaseMod()

        //market.accessibilityMod.modifyFlat(modId, ACCESS_MALUS, nameForModifier)

        val mult = getDeficitMult(Commodities.SUPPLIES)
        var extra = ""
        if (mult != 1f) {
            val com = getMaxDeficit(Commodities.SUPPLIES).one
            extra = " (" + getDeficitText(com).lowercase(Locale.getDefault()) + ")"
        }
        val bonus = DEFENSE_BONUS
        market.stats.dynamic.getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(modId, 1f + bonus * mult, getNameForModifier() + extra)
        market.stats.dynamic.getMod(Stats.OFFICER_PROB_MOD).modifyFlat(getModId(0), OFFICER_PROB)

        market.memoryWithoutUpdate.setFlagWithReason(MemFlags.MARKET_PATROL, modId, true, -1f)
        market.memoryWithoutUpdate.setFlagWithReason(MemFlags.MARKET_MILITARY, modId, true, -1f)
    }

    override fun unapply() {
        super.unapply()

        market.accessibilityMod.unmodify(modId)
        market.stats.dynamic.getStat(Stats.COMBAT_FLEET_SPAWN_RATE_MULT).unmodify(modId)
        market.removeTransientImmigrationModifier(this)
    }

    fun MarketAPI.isOversized() = size > MAX_SIZE

    override fun isFunctional(): Boolean {
        return super.isFunctional() && !market.isOversized()
    }

    override fun isAvailableToBuild(): Boolean {
        if (!market.faction.knowsIndustry(spec.id)) return false
        if (market.isOversized()) return false

        return super.isAvailableToBuild()
    }

    override fun getUnavailableReason(): String? {
        if (market.isOversized()) return "Must be at most size ${MAX_SIZE - 1}"
        return super.getUnavailableReason()
    }

    override fun showWhenUnavailable(): Boolean {
        if (!market.faction.knowsIndustry(spec.id)) return false

        return super.showWhenUnavailable()
    }

    override fun spawnFleet(route: RouteManager.RouteData): CampaignFleetAPI? {
        val custom = route.getCustom() as PatrolFleetData
        val type = custom.type

        val random = route.getRandom()

        val fleet = createPatrol(type, market.factionId, route, market, null, random)

        if (fleet == null || fleet.isEmpty) return null

        fleet.addEventListener(this)

        market.containingLocation.addEntity(fleet)
        fleet.facing = Math.random().toFloat() * 360f
        // this will get overridden by the patrol assignment AI, depending on route-time elapsed etc
        fleet.setLocation(market.primaryEntity.location.x, market.primaryEntity.location.y)

        //fleet.addScript(PatrolAssignmentAIV4(fleet, route))
        // ADDITION
        if (fleet.memoryWithoutUpdate.getString(MemFlags.MEMORY_KEY_FLEET_TYPE) == FleetTypes.PATROL_SMALL) {
            fleet.addScript(PatrolAssignmentAIV4(fleet, route))
        } else {
            fleet.addScript(OutpostDefenseAssignmentAI(fleet, route, market.primaryEntity))
            fleet.memoryWithoutUpdate[MemFlags.FLEET_NO_MILITARY_RESPONSE] = true
            fleet.memoryWithoutUpdate.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true)
        }

        fleet.memoryWithoutUpdate.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true, 0.3f)


        //market.getContainingLocation().addEntity(fleet);
        //fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);
        if (custom.spawnFP <= 0) {
            custom.spawnFP = fleet.fleetPoints
        }

        return fleet
    }

    override fun addImproveDesc(info: TooltipMakerAPI?, mode: Industry.ImprovementDescriptionMode?) {
        info?.addPara(
            "Allows %s light patrol.",
            0f,
            Misc.getHighlightColor(),
            "one"
        )
    }

    override fun applyImproveModifiers() {
        return
    }

    override fun modifyIncoming(
        market: MarketAPI?,
        incoming: PopulationComposition?
    ) {
        if (market != this.market) return
        if (incoming == null) return
        if (market.size >= MAX_SIZE) {
            incoming.weight.modifyMult(modId, 0f, "$nameForModifier - Max Size")
        }
    }

    override fun addPostDemandSection(tooltip: TooltipMakerAPI?, hasDemand: Boolean, mode: IndustryTooltipMode?) {
        if (mode != IndustryTooltipMode.NORMAL || isFunctional) {
            addStabilityPostDemandSection(tooltip, hasDemand, mode)
            addGroundDefensesImpactSection(tooltip, DEFENSE_BONUS, Commodities.SUPPLIES)

            tooltip?.addPara("Creates %s instead of patrols", 10f, Misc.getHighlightColor(), "large planetary defense fleets")
            tooltip?.addPara("%s targeting this system by %s days", 10f, Misc.getPositiveHighlightColor(), "Delays hostile actions", "${(BASE_DELAY_INCR * BASE_DELAY_MULT).trimHangingZero()}")

            //tooltip?.addPara("Reduces accessibility by %s", 10f, Misc.getNegativeHighlightColor(), "${(-ACCESS_MALUS * 100f).trimHangingZero()}%")
        }
        tooltip?.addPara("Caps colony size to %s", 10f, Misc.getNegativeHighlightColor(), "$MAX_SIZE")
    }
}