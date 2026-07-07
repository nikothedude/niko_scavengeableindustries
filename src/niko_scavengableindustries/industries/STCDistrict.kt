package niko_scavengableindustries.industries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.FleetAssignment
import com.fs.starfarer.api.campaign.JumpPointAPI
import com.fs.starfarer.api.campaign.RepLevel
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.campaign.intel.events.ht.HyperspaceTopographyEventIntel
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.WeightedRandomPicker
import niko_scavengableindustries.utils.MathUtils
import org.magiclib.kotlin.isTrader
import kotlin.collections.iterator
import kotlin.math.floor

/// Only one per star sys
/// Improves sensor range burn drive for trade fleets and access for all planets
/// Also adds little blurbs when youre ne route to a planet
/// "Blah blah heres the flightpath"
/// Probably also do this for other fleets.
/// TODO finish
class STCDistrict: BaseIndustry() {

    companion object {
        val EN_ROUTE_TO_JUMP_POINT_BLURBS = mutableMapOf(
            Pair("Hyperspace turbulence is a bit bumpy today, %HONORIFIC. Stay safe.", 100f),
            Pair("Sending a open-request to your jumpgate, %HONORIFIC.", 100f),
            Pair("Adjust heading by point three degrees left for optimal slipstream.", 100f),
            Pair("Minor variations in your drive field, please check before making final approach.", 100f),
            Pair("Reporting minor pirate activity in hyperspace %HONORIFIC, keep your sensors sharp.", 100f),
        )
        val EN_ROUTE_TO_MARKET_BLURBS = mutableMapOf(
            Pair("Route to %MARKETNAME confirmed, %HONORIFIC. Mapping you a clean path.", 100f),
            Pair("Deep-space fluctuations are a little elevated today %HONORIFIC, recommend you stay in the travel-lanes.", 100f),
            Pair("%MARKETNAME informed of your arrival %HONORIFIC. Have a safe trip.", 100f),
            Pair("Adjust heading by point three degrees left for optimal slipstream.", 100f),
            Pair("Minor variations in your drive field, please check before making final approach.", 100f),
            Pair("ECM may force STC to deny your approach - remember to disable your emitters before landfall.", 50f),
            Pair("Remember to put your hands on your console and smile if a patrol pulls you over, %HONORIFIC.", 10f),
            Pair("%MARKETNAME Landing pads 95C through 12M cleared for your approach, %HONORIFIC.", 10f),
            Pair("%MARKETNAME Landing pads 12A through 53A cleared for your approach, %HONORIFIC.", 10f),
            Pair("%MARKETNAME Landing pads 01X through 93X cleared for your approach, %HONORIFIC.", 10f),
            Pair("%MARKETNAME Landing pads 09F through 32G cleared for your approach, %HONORIFIC.", 10f),
            Pair("%MARKETNAME Landing pads 32B through 95A cleared for your approach, %HONORIFIC.", 10f),
        )

        fun processString(fleet: CampaignFleetAPI, toProcess: String, target: SectorEntityToken): String {
            var toProcess = toProcess
            toProcess = toProcess.replace(
                "%HONORIFIC",
                if (fleet.commander.isFemale) "ma'am" else "sir"
            )
            if (target.market != null) {
                toProcess = toProcess.replace(
                    "%MARKETNAME",
                    target.market.name
                )
            }

            return toProcess
        }

        const val BURN_SPEED_BONUS_HEADING_TO_WAYPOINT = 3
        const val MIN_INTERVAL = 0.2f
        const val MARKET_ACCESS_INCREMENT = 0.10f
        const val IMPROVED_BURN_INCR = 1
        const val ALPHA_BURN_INCR = 1
        const val SLIPSTREAM_RANGE_BONUS = 10f
    }

    val affectedMarkets = HashSet<MarketAPI>()

    override fun apply() {
        super.apply(true)

        if (!isFunctional) {
            return
        }
        if (isSuperceded()) {
            return
        }

        val size = market.size
        demand(Commodities.CREW, size - 1)
        demand(Commodities.FUEL, size)
        val mult = getOurDeficitMult()
        var extra = ""
        val deficit = getMaxDeficit(Commodities.CREW, Commodities.FUEL)
        if (deficit.two > 0f) {
            extra = " (${deficit.one} shortages)"
        }
        val accessString = "$nameForModifier$extra"

        val markets = Misc.getMarketsInLocation(market.containingLocation)
        for (iterMarket in markets) {
            if (iterMarket.faction.getRelationshipLevel(market.faction).isAtBest(RepLevel.INHOSPITABLE)) continue
            if (iterMarket.size > market.size) continue

            iterMarket.accessibilityMod.modifyFlat(id, MARKET_ACCESS_INCREMENT * mult, "${market.name} $accessString")
            affectedMarkets += iterMarket
        }

        val intel = HyperspaceTopographyEventIntel.get()
        if (intel != null && intel.isStageActive(HyperspaceTopographyEventIntel.Stage.SLIPSTREAM_DETECTION)) {
            market.stats.dynamic.getMod(Stats.SLIPSTREAM_REVEAL_RANGE_LY_MOD).modifyFlat(
                getModId(0), SLIPSTREAM_RANGE_BONUS, nameForModifier
            )
        }
    }

    fun getOurDeficitMult() = getDeficitMult(
        Commodities.CREW,
        Commodities.FUEL
    )

    override fun unapply() {
        super.unapply()

        for (iterMarket in affectedMarkets) {
            iterMarket.accessibilityMod.unmodify(id)
        }

        affectedMarkets.clear()

        market.stats.dynamic.getMod(Stats.SLIPSTREAM_REVEAL_RANGE_LY_MOD).unmodifyFlat(getModId(0))
    }

    val checkInterval = IntervalUtil(MIN_INTERVAL, MIN_INTERVAL + 0.2f) // days
    override fun advance(amount: Float) {
        super.advance(amount)
        if (!isFunctional) return
        if (isSuperceded()) {
            return
        }

        checkInterval.advance(Misc.getDays(amount))
        if (checkInterval.intervalElapsed()) {
            checkFleets(amount)
        }
    }

    private fun checkFleets(amount: Float) {
        val containing = market.containingLocation ?: return
        val mult = getOurDeficitMult()
        for (fleet in containing.fleets) {
            if (!fleetValidTarget(fleet)) continue

            var burnBonus = BURN_SPEED_BONUS_HEADING_TO_WAYPOINT
            if (isImproved) burnBonus += IMPROVED_BURN_INCR
            if (aiCoreId == Commodities.ALPHA_CORE) burnBonus += ALPHA_BURN_INCR
            fleet.stats.addTemporaryModFlat(
                MIN_INTERVAL + 1f,
                id,
                "${market.name} $nameForModifier",
                floor((burnBonus * mult)),
                fleet.stats.fleetwideMaxBurnMod
            )

            if (MathUtils.prob(5)) {
                doBlurb(fleet)
            }
        }
    }

    private fun doBlurb(fleet: CampaignFleetAPI) {
        val target = getDest(fleet) ?: return
        if (target.containingLocation != market.containingLocation) return
        val map: MutableMap<String, Float>
        if (target is JumpPointAPI) {
            map = EN_ROUTE_TO_JUMP_POINT_BLURBS
        } else {
            map = EN_ROUTE_TO_MARKET_BLURBS
        }

        val picker = WeightedRandomPicker<String>()
        for (entry in map) {
            picker.add(entry.key, entry.value)
        }

        val picked = picker.pick()
        val output = processString(fleet, picked, target)

        fleet.addFloatingText(
            output, market.faction.baseUIColor.darker(), 5f, true
        )
    }

    private fun fleetValidTarget(fleet: CampaignFleetAPI): Boolean {
        // check faction first
        if (fleet.isTrader()) {
            if (fleet.faction.isHostileTo(market.faction)) {
                return false
            }
        } else if (fleet.faction.getRelationshipLevel(market.faction.id).isAtBest(RepLevel.INHOSPITABLE)) {
            return false
        }

        if (!fleet.isDestinationValid()) return false
        if (!fleet.isTransponderOn) return false

        return true
    }

    fun getDest(fleet: CampaignFleetAPI): SectorEntityToken? {
        val dest: SectorEntityToken?
        if (fleet.isPlayerFleet) {
            if (Global.getSector().campaignUI.isFollowingDirectCommand) return null
            dest = Global.getSector().campaignUI.currentCourseTarget
        } else {
            if (fleet.currentAssignment == null) return null
            if (fleet.currentAssignment.assignment == FleetAssignment.PATROL_SYSTEM) return null
            val target = fleet.currentAssignment?.target ?: return null
            if (org.lazywizard.lazylib.MathUtils.getDistance(
                fleet,
                target,
            ) < 250f) return null
            dest = fleet.currentAssignment?.target
        }

        return dest
    }

    private fun CampaignFleetAPI.isDestinationValid(): Boolean {
        return isDestinationValidForBurn(getDest(this))
    }

    fun isDestinationValidForBurn(entity: SectorEntityToken?): Boolean {
        if (entity == null) return false

        if (entity is JumpPointAPI) {
            return true
        }
        val foundMarket = entity.market ?: return false
        return !foundMarket.faction.getRelationshipLevel(market.faction).isAtBest(RepLevel.INHOSPITABLE)
    }

    fun isSuperceded(): Boolean {
        val markets = Misc.getMarketsInLocation(market.containingLocation)
        for (iterMarket in markets) {
            if (iterMarket == market) continue
            if (iterMarket.faction.id != market.faction.id) continue
            if (iterMarket.hasIndustry(spec.id) && iterMarket.size >= market.size) {
                return true
            }
        }
        return false
    }

    override fun addPostDemandSection(
        tooltip: TooltipMakerAPI,
        hasDemand: Boolean,
        mode: Industry.IndustryTooltipMode
    ) {
        super.addPostDescriptionSection(tooltip, mode)

        tooltip.addPara(
            "Improves burn speed of friendly fleets in-system by %s provided they are %s (directly flying towards a jump-point or colony) and have their %s.",
            10f,
            Misc.getHighlightColor(),
            "$BURN_SPEED_BONUS_HEADING_TO_WAYPOINT", "moving along a dedicated travel-lane", "transponder on"
        )

        tooltip.addPara(
            "Additionally increases accessibility for all smaller-or-equal-size colonies in-system by %s.",
            10f,
            Misc.getHighlightColor(),
            "${(MARKET_ACCESS_INCREMENT * 100f).toInt()}%"
        )

        tooltip.addPara(
            "Increases the range at which slipstreams are detected around the colony by %s, once "
                    + "the capability to do so is available.", 10f, Misc.getHighlightColor(),
            "" + SLIPSTREAM_RANGE_BONUS.toInt()
        )

        if (isSuperceded()) {
            tooltip.addPara(
                "A larger ${spec.name} exists in-system. This industry is inert.",
                10f
            ).color = Misc.getNegativeHighlightColor()
        }
    }

    override fun canImprove(): Boolean {
        return true
    }

    override fun addImproveDesc(info: TooltipMakerAPI?, mode: Industry.ImprovementDescriptionMode?) {
        info?.addPara(
            "Improves burn bonus by %s.",
            0f,
            Misc.getHighlightColor(),
            "$IMPROVED_BURN_INCR"
        )
    }

    override fun addAlphaCoreDescription(tooltip: TooltipMakerAPI?, mode: Industry.AICoreDescriptionMode?) {
        if (tooltip == null) return

        val opad = 10f
        val highlight = Misc.getHighlightColor()

        var pre = "Alpha-level AI core currently assigned. "
        if (mode == Industry.AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. "
        }
        if (mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP || mode == Industry.AICoreDescriptionMode.MANAGE_CORE_TOOLTIP) {
            val coreSpec = Global.getSettings().getCommoditySpec(aiCoreId)
            val text = tooltip.beginImageWithText(coreSpec.iconName, 48f)
            text.addPara(
                pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                        "Increases burn bonus by %s", 0f, highlight,
                "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION, "$ALPHA_BURN_INCR"
            )
            tooltip.addImageWithText(opad)
            return
        }

        tooltip.addPara(
            pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                    "Increases burn bonus by %s.", opad, highlight,
            "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION, "$ALPHA_BURN_INCR"
        )
    }

    override fun showWhenUnavailable(): Boolean {
        if (isSuperceded()) return true
        if (!market.faction.knowsIndustry(spec.id)) return false

        return super.showWhenUnavailable()
    }

    override fun isAvailableToBuild(): Boolean {
        if (!market.faction.knowsIndustry(spec.id)) return false
        if (isSuperceded()) return false

        return super.isAvailableToBuild()
    }

    override fun getUnavailableReason(): String? {
        if (isSuperceded()) return "A larger STC Complex exists in this system"

        return super.getUnavailableReason()
    }

    override fun isFunctional(): Boolean {
        return !isSuperceded() && super.isFunctional()
    }

    override fun getPatherInterest(): Float {
        return super.patherInterest + 2f
    }
}