package niko_scavengableindustries.industries.SpyBureau

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BattleAPI
import com.fs.starfarer.api.campaign.CampaignEventListener
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.campaign.RepLevel
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener
import com.fs.starfarer.api.campaign.listeners.FleetEventListener
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import data.scripts.DelayedExecution
import niko_scavengableindustries.industries.SpyBureau.SpyFleetAssignmentAI.Companion.STABILITY_MIN
import niko_scavengableindustries.utils.StringUtils.toPercent
import org.magiclib.kotlin.getFactionMarkets
import org.magiclib.kotlin.getMarketsInLocation

/// Sends fleets to go spy on other factions, doing some piracy on the way.
/// Destabilizes hostile markets, blows up trade fleets, returns with their goods (see privateer base for an example)
/// Provides sensor range boost/sensor profile reduction to your fleet in hostile systems when improved.
/// 2 industry cost.
/// Only installable on a market with a high command.
/// Only one allowed.
/// Probably causes a crisis if you use it, idk
class SpyBureau: BaseIndustry(), FleetEventListener, EconomyTickListener {

    companion object {
        const val SENSOR_MULT = 0.5f
        const val DESTROYED_RESPAWN_TIME = 30f // days - bonus on top of usual delay
        const val TIME_BETWEEN_FLEET_SPAWNS = 20f
        const val MAX_FLEETS = 3
        const val COMBAT_POINTS = 120f
        const val SELF_STABILITY_BONUS_INCR = 2
        const val STABILITY_BONUS = 1

        const val BONUS_FLEETS_IMPROVED = 2
        const val MARKETS_PER_ALPHA_UNREST_BURST = 3
        const val ALPHA_UNREST_BURST = 2
        const val DESTAB_DIST_LY = 10f
        const val ALPHA_UNREST_BURST_MIN_STABILITY = 2

        fun marketIsValidTarget(market: MarketAPI, ourFaction: FactionAPI): Boolean {
            if (!market.faction.isHostileTo(ourFaction)) return false
            if (!market.isInEconomy) return false
            if (market.isHidden) return false
            if (market.stability.modified < STABILITY_MIN) return false

            return true
        }
    }

    val fleets = HashSet<CampaignFleetAPI>()
    var affectedSabotageMarkets = HashSet<MarketAPI>()

    override fun apply() {
        super.apply(true)

        val size = market.size
        class AddScript() : DelayedExecution(IntervalUtil(0f, 0f), useDays = false) {
            override fun executeImpl() {
                if (!market.hasIndustry("NSI_SpyBureauSecondInd")) {
                    market.addIndustry("NSI_SpyBureauSecondInd")
                }
            }

            override fun runWhilePaused(): Boolean = true
        }
        AddScript().start()

        demand(Commodities.CREW, size + 2)
        demand(Commodities.SUPPLIES, size + 1)
        demand(Commodities.SHIPS, size + 1)
        demand(Commodities.DRUGS, size - 2)

        if (!Global.getSector().listenerManager.hasListener(this)) {
            Global.getSector().listenerManager.addListener(this, false)
        }

        val saboDeficitMult = getDeficitMult(
            Commodities.SUPPLIES,
            Commodities.CREW
        )

        affectedSabotageMarkets = SpyOutpost.destabilizeNearbyMarkets(
            market,
            DESTAB_DIST_LY,
            saboDeficitMult
        )
    }

    override fun unapply() {
        super.unapply()

        class RemoveScript() : DelayedExecution(IntervalUtil(0f, 0f), useDays = false) {
            override fun executeImpl() {
                if (!market.hasIndustry("NSI_SpyBureauSecondInd")) {
                    market.removeIndustry("NSI_SpyBureauSecondInd", null, false)
                }
            }

            override fun runWhilePaused(): Boolean = true
        }
        RemoveScript().start()

        Global.getSector().listenerManager.removeListener(this)
        SpyOutpost.resetDestabilization(affectedSabotageMarkets)
        affectedSabotageMarkets.clear()
    }

    val interval = IntervalUtil(0.1f, 0.12f)
    val stabInterval = IntervalUtil(1f, 1.1f)
    val spawnInterval = IntervalUtil(TIME_BETWEEN_FLEET_SPAWNS, TIME_BETWEEN_FLEET_SPAWNS + 0.1f)
    var respawnTime = 0f

    val affectedMarkets = HashSet<MarketAPI>()

    override fun advance(amount: Float) {
        super.advance(amount)

        if (!isFunctional) return

        handleFleetSpawns(amount)

        stabInterval.advance(Misc.getDays(amount))
        if (stabInterval.intervalElapsed()) {
            tryStabilityMod()
        }
        interval.advance(Misc.getDays(amount))
        if (!interval.intervalElapsed()) return

        val playerFleet = Global.getSector().playerFleet ?: return
        val playerLoc = playerFleet.containingLocation ?: return
        if (playerLoc.getMarketsInLocation().none { it.faction.isHostileTo(market.faction) }) return

        val mult = getDeficitMult(
            Commodities.CREW,
            Commodities.SUPPLIES
        )
        val bonusText = if (mult < 1) " (Shortages)" else ""

        for (fleet in playerLoc.fleets) {
            if (shouldAffectFleet(fleet)) {
                fleet.stats.addTemporaryModMult(
                    1f,
                    "${market.id}_$id",
                    "${market.faction.displayName} Spy Bureau$bonusText",
                    (SENSOR_MULT + (SENSOR_MULT - (mult * SENSOR_MULT))),
                    fleet.stats.detectedRangeMod
                )
            }
        }
    }

    private fun tryStabilityMod() {
        for (iterMarket in affectedMarkets) {
            iterMarket.stability.unmodify(id)
        }
        affectedMarkets.clear()

        val mult = getDeficitMult(
            Commodities.DRUGS
        )
        val extra = if (mult < 1) " (Shortages)" else ""
        for (iterMarket in market.faction.getFactionMarkets()) {
            if (iterMarket == market) continue
            if (iterMarket.size > market.size) continue

            iterMarket.stability.modifyFlat(id, (STABILITY_BONUS * mult).toInt().toFloat(), "${market.name} $nameForModifier$extra")
            affectedMarkets += iterMarket
        }
        market.stability.modifyFlat(id, ((STABILITY_BONUS + SELF_STABILITY_BONUS_INCR) * mult).toInt().toFloat(), "$nameForModifier$extra")
        affectedMarkets += market
    }

    private fun handleFleetSpawns(amount: Float) {
        val mult = getDeficitMult(
            Commodities.CREW,
            Commodities.SHIPS
        )
        val days = Misc.getDays(amount * mult)

        if (respawnTime > 0f) {
            respawnTime = (respawnTime - days).coerceAtLeast(0f)
            return
        }

        var fleetsMax = MAX_FLEETS
        if (isImproved) fleetsMax += BONUS_FLEETS_IMPROVED
        if (fleets.size >= fleetsMax) return

        spawnInterval.advance(days)
        if (spawnInterval.intervalElapsed()) {
            trySpawningFleet()
        }
    }

    private fun trySpawningFleet() {
        val targetSys = getTargetSys() ?: return

        val params = FleetParamsV3(
            market,
            FleetTypes.TRADE_SMALL,
            COMBAT_POINTS,
            25f,
            4f,
            0f,
            0f,
            0f,
            0f,
        )
        params.ignoreMarketFleetSizeMult = true

        val fleet = FleetFactoryV3.createFleet(params)
        fleet.setFaction(Factions.INDEPENDENT, true)

        market.containingLocation.addEntity(fleet)
        val loc = market.primaryEntity.location
        fleet.setLocation(loc.x, loc.y)

        val script = SpyFleetAssignmentAI(fleet, market.faction)
        script.start()
        SpyFleetAssignmentAI.giveInitialAssignments(fleet, targetSys, script)
        fleet.stats.sensorRangeMod.modifyMult("NSI_spyFleet", 2f)

        fleet.memoryWithoutUpdate["\$NSI_spyFleet"] = true
        fleet.memoryWithoutUpdate[MemFlags.FLEET_IGNORED_BY_FACTION] = market.faction.id
        fleet.memoryWithoutUpdate[MemFlags.FLEET_IGNORES_OTHER_FLEETS] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_NO_REP_IMPACT] = true
        if (market.faction.id == Factions.PLAYER) {
            fleet.memoryWithoutUpdate.set("\$genericHail", true)
            fleet.memoryWithoutUpdate.set("\$genericHail_openComms", "NSI_SpyFleetHail")
        }
        fleet.addEventListener(this)
        fleets += fleet
    }

    private fun getTargetSys(): StarSystemAPI? {
        for (iterMarket in Global.getSector().economy.marketsCopy.shuffled()) {
            if (!marketIsValidTarget(iterMarket, market.faction)) continue
            if (iterMarket.starSystem == null) continue
            return iterMarket.starSystem
        }
        return null
    }

    private fun shouldAffectFleet(fleet: CampaignFleetAPI): Boolean {
        if (fleet.faction.getRelationshipLevel(market.faction).isAtWorst(RepLevel.WELCOMING)) return true
        if (fleet.memoryWithoutUpdate["\$NSI_spyFleet"] == true) return true

        return false
    }

    override fun addPostDemandSection(
        tooltip: TooltipMakerAPI?,
        hasDemand: Boolean,
        mode: Industry.IndustryTooltipMode?
    ) {
        super.addPostDemandSection(tooltip, hasDemand, mode)

        if (tooltip == null) return

        tooltip.addPara(
            "Sends %s to hostile systems that will opportunistically %s and %s. Max of %s concurrent fleets.",
            10f,
            Misc.getHighlightColor(),
            "spy fleets", "attack trade fleets", "destabilize colonies", "$MAX_FLEETS"
        )

        tooltip.addPara(
            "Reduces detected-at range of %s in %s by %s - including your own.",
            10f,
            Misc.getHighlightColor(),
            "all friendly fleets", "hostile systems", toPercent(SENSOR_MULT)
        )

        tooltip.addPara(
            "Reduces stability and accessibility of hostile colonies within %s by %s and %s respectively. This %s can decivilize colonies, unlike your fleets.",
            10f,
            Misc.getHighlightColor(),
            "${DESTAB_DIST_LY.toInt()} LY", "${-SpyOutpost.STAB_MALUS.toInt()}", "${(-SpyOutpost.ACCESS_MALUS * 100f).toInt()}%", "can"
        )

        tooltip.addPara(
            "Additionally increases stability of all %s by %s. This colony instead receives a bonus of %s.",
            10f,
            Misc.getHighlightColor(),
            "same-faction, equal-or-smaller colonies", "$STABILITY_BONUS", "${STABILITY_BONUS + SELF_STABILITY_BONUS_INCR}"
        )

        tooltip.addPara(
            "Finally, enables the construction of %s.",
            10f,
            Misc.getHighlightColor(),
            "Spy Outposts"
        )

        tooltip.addPara(
            "Only one ${spec.name} can exist at a time.",
            10f
        )
    }

    fun isSuperceded(): Boolean {
        return market.faction.getFactionMarkets().any { it != market && it.hasIndustry(spec.id) }
    }

    override fun isFunctional(): Boolean {
        return !isSuperceded() && super.isFunctional()
    }

    override fun canImprove(): Boolean {
        return true
    }

    override fun addImproveDesc(info: TooltipMakerAPI?, mode: Industry.ImprovementDescriptionMode?) {
        info?.addPara("Increases max fleets by %s.", 0f, Misc.getHighlightColor(), "$BONUS_FLEETS_IMPROVED")
    }

    override fun addAlphaCoreDescription(tooltip: TooltipMakerAPI?, mode: AICoreDescriptionMode?) {
        if (tooltip == null) return

        val opad = 10f
        val highlight = Misc.getHighlightColor()

        var pre = "Alpha-level AI core currently assigned. "
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. "
        }
        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP || mode == AICoreDescriptionMode.MANAGE_CORE_TOOLTIP) {
            val coreSpec = Global.getSettings().getCommoditySpec(aiCoreId)
            val text = tooltip.beginImageWithText(coreSpec.iconName, 48f)
            text.addPara(
                pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                        "Randomly applies unrest to hostile colonies every month.", 0f, highlight,
                "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION
            )
            tooltip.addImageWithText(opad)
            return
        }

        tooltip.addPara(
            pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                    "Randomly applies unrest to hostile colonies every month.", opad, highlight,
            "" + ((1f - UPKEEP_MULT) * 100f).toInt() + "%", "" + DEMAND_REDUCTION
        )
    }

    override fun reportFleetDespawnedToListener(
        fleet: CampaignFleetAPI?,
        reason: CampaignEventListener.FleetDespawnReason?,
        param: Any?
    ) {
        if (fleet == null) return
        if (!fleets.contains(fleet)) return

        if (reason == CampaignEventListener.FleetDespawnReason.DESTROYED_BY_BATTLE) {
            respawnTime += DESTROYED_RESPAWN_TIME
        }

        fleet.removeEventListener(this)
        fleets -= fleet
    }

    override fun reportBattleOccurred(
        fleet: CampaignFleetAPI?,
        primaryWinner: CampaignFleetAPI?,
        battle: BattleAPI?
    ) {

    }

    override fun reportEconomyTick(iterIndex: Int) {
        return
    }

    override fun reportEconomyMonthEnd() {
        if (aiCoreId != Commodities.ALPHA_CORE) return

        var picksLeft = MARKETS_PER_ALPHA_UNREST_BURST
        for (iterMarket in Global.getSector().economy.marketsCopy.shuffled()) {
            if (picksLeft <= 0f) break
            if (!marketIsValidTarget(iterMarket, market.faction)) continue
            applyAlphaUnrestToMarket(iterMarket)
            picksLeft--
        }
    }

    private fun applyAlphaUnrestToMarket(target: MarketAPI) {
        RecentUnrest.get(target).add(ALPHA_UNREST_BURST, "Destabilized by covert action")
    }

    override fun isAvailableToBuild(): Boolean {
        if (!market.faction.knowsIndustry(spec.id)) return false
        if (isSuperceded()) return false

        return super.isAvailableToBuild()
    }

    override fun getUnavailableReason(): String? {
        if (isSuperceded()) return "Only one can exist at a time"

        return super.getUnavailableReason()
    }

    override fun showWhenUnavailable(): Boolean {
        if (!market.faction.knowsIndustry(spec.id)) return false

        return super.showWhenUnavailable()
    }
}