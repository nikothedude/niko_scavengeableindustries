package niko_scavengableindustries.industries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.impl.campaign.HassleNPCScript
import com.fs.starfarer.api.impl.campaign.NPCHassler
import com.fs.starfarer.api.impl.campaign.econ.impl.Cryosanctum
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.campaign.intel.events.*
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel.EventStageData
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel.HAERandomEventData
import com.fs.starfarer.api.impl.campaign.intel.group.FGRaidAction.FGRaidType
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel.FGIEventListener
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI.GenericRaidParams
import com.fs.starfarer.api.impl.campaign.intel.group.SindrianDiktatPunitiveExpedition
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission.FleetStyle
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.BombardType
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator
import com.fs.starfarer.api.util.Misc
import niko_scavengableindustries.utils.MathUtils
import org.magiclib.kotlin.getFactionMarkets
import sound.int
import java.awt.Color
import java.util.*

class NSICryosanctum: Cryosanctum() {

    companion object {
        const val TRUSTED_SOURCE_INCOME_MULT = 1.5f
        const val HASSLE_REASON = "NSICryosanctumProtesters"

        fun getBiggestSanctum(): MarketAPI? {
            return getThreateningSanctums().maxByOrNull { it.size }
        }
        fun getNomios(): MarketAPI? = Global.getSector().economy.getMarket("nomios")
        fun getSanctums(): List<MarketAPI> = Global.getSector().playerFaction.getFactionMarkets().filter { it.hasIndustry("NSI_cryosanctum") }
        fun getThreateningSanctums(): List<MarketAPI> = getSanctums().filter { it.getIndustry("NSI_cryosanctum").isFunctional }
    }

    override fun isAvailableToBuild(): Boolean {
        return market.faction.knowsIndustry(spec.id)
    }

    override fun isIndustry(): Boolean {
        return true
    }

    override fun applyIncomeAndUpkeep(sizeOverride: Float) {
        super.applyIncomeAndUpkeep(sizeOverride)

        if (Global.getSector().memoryWithoutUpdate.getBoolean("\$NSI_hasCryosanctumBuff")) {
            income.modifyMult("NSITrustedSource", TRUSTED_SOURCE_INCOME_MULT, "Trusted Cryosanctum")
        }
    }

    class CrisisFactor(intel: HostileActivityEventIntel): BaseHostileActivityFactor(intel), FGIEventListener {
        override fun shouldShow(intel: BaseEventIntel?): Boolean {
            return getProgress(intel) > 0 && getNomios() != null && getThreateningSanctums().isNotEmpty()
        }

        override fun getDesc(intel: BaseEventIntel?): String? {
            return "Nomios Cryosanctum"
        }

        override fun getMainRowTooltip(intel: BaseEventIntel?): TooltipCreator {
            return object : BaseFactorTooltip() {
                override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any?) {
                    val opad = 10f

                    tooltip.addPara(
                        "%s has sat in decadent monopoly for tens of cycles, the only functioning Cryosanctum in the sector. " +
                                "Your creation of a sanctum on %s threatens this, and now they have begun a defamation campaign against you.",
                        0f,
                        getNomios()?.faction?.baseUIColor ?: Misc.getNegativeHighlightColor(),
                        "Nomios", "${getBiggestSanctum()?.name}"
                    ).setHighlightColors(
                        getNomios()?.faction?.baseUIColor ?: Misc.getNegativeHighlightColor(),
                        getBiggestSanctum()?.faction?.baseUIColor ?: Misc.getNegativeHighlightColor()
                    )
                    tooltip.addPara(
                        "Fleets protesting your use of a Cryosanctum, paid for by %s, may be found in your system, harassing " +
                                "fleets and forcing them to sign petitions.",
                        opad,
                        getNomios()?.faction?.baseUIColor ?: Misc.getNegativeHighlightColor(),
                        "Nomios"
                    )
                }
            }
        }

        override fun getDescColor(intel: BaseEventIntel?): Color? {
            return getNomios()?.faction?.baseUIColor ?: Misc.getNegativeHighlightColor()
        }

        override fun createFleet(system: StarSystemAPI?, random: Random?): CampaignFleetAPI? {
            val nomios = getNomios() ?: return null

            //int difficulty = 0 + (int) Math.max(1f, Math.round(f * 6f));
            var difficulty = 1

            val m = FleetCreatorMission(random)
            m.beginFleet()

            val loc = system!!.location
            val factionId = nomios.faction.id

            m.createStandardFleet(difficulty, factionId, loc)
            m.triggerSetFleetType(FleetTypes.PATROL_SMALL)
            m.triggerSetPatrol()
            //m.triggerSetFleetHasslePlayer(HASSLE_REASON)
            m.triggerSetFleetFlag("\$NSICryosanctumProtesters")
            m.triggerFleetSetPatrolActionText("protesting")

            m.triggerFleetAllowLongPursuit()
            m.triggerMakeLowRepImpact()

            //m.triggerMakeHostile();
            //m.triggerMakeHostileWhileTransponderOff();
            val fleet = m.createFleet()

            if (fleet != null) {
                fleet.name = "Protesters"
                val params = HassleNPCScript.HassleParams()
                params.fleetAction = "harassing"
                params.targetAction = "being harassed"
                val hassle = NPCHassler(fleet, params, system)
                hassle.getParams().crDamageMult = 0f
                fleet.addScript(hassle)
            }

            return fleet
        }
        override fun addBulletPointForEventReset(
            intel: HostileActivityEventIntel?, stage: EventStageData?, info: TooltipMakerAPI,
            mode: ListInfoMode?, isUpdate: Boolean, tc: Color?, initPad: Float
        ) {
            info.addPara("Cryosanctum raid averted", tc, initPad)
        }

        override fun addStageDescriptionForEvent(
            intel: HostileActivityEventIntel?,
            stage: EventStageData,
            info: TooltipMakerAPI
        ) {
            if (stage.rollData !is HAERandomEventData) return
            val data = stage.rollData as HAERandomEventData
            val target = data.custom as? MarketAPI? ?: return


            //MarketAPI target = pickTargetMarket();

            val from: MarketAPI = getNomios() ?: return

            val c = Global.getSector().getFaction(Factions.PIRATES).baseUIColor

            var small = 0f
            val opad = 10f

            small = 8f

            info.addPara(
                "You've received intel that an (alledgedly) Nomios-sponsored pirate raid will soon launch against %s, with the goal of " +
                        "attacking the %s and %s in your ability to guarantee your client's safeties.",
                small, c, "${target.name}", "Cryosanctum", "ruining trust"
            ).setHighlightColors(
                target.faction.baseUIColor,
                Misc.getHighlightColor(),
                Misc.getNegativeHighlightColor()
            )

            info.addPara(
                "Defeating this incursion, however, would more than likely %s trust, and result in %s and, thus, %s in the future.",
                small, c, "reinforce", "more patronage", "higher income"
            ).setHighlightColors(
                Misc.getPositiveHighlightColor(),
                Misc.getPositiveHighlightColor(),
                Misc.getPositiveHighlightColor()
            )

            stage.beginResetReqList(info, true, "crisis", opad)
            info.addPara("%s ceases existence", 0f, getNomios()!!.faction.baseUIColor, "Nomios")
            stage.endResetReqList(info, false, "crisis", -1, -1)

            addBorder(info, Global.getSector().getFaction(Factions.PIRATES).baseUIColor)
        }

        override fun getEventStageIcon(intel: HostileActivityEventIntel?, stage: EventStageData?): String? {
            return Global.getSector().getFaction(Factions.PIRATES).crest
        }

        override fun getStageTooltipImpl(intel: HostileActivityEventIntel?, stage: EventStageData): TooltipCreator? {
            if (stage.id === HostileActivityEventIntel.Stage.HA_EVENT) {
                return getDefaultEventTooltip("Cryosanctum Raid", intel, stage)
            }
            return null
        }

        override fun getEventFrequency(intel: HostileActivityEventIntel?, stage: EventStageData?): Float {
            if (stage!!.id === HostileActivityEventIntel.Stage.HA_EVENT) {
                if (getBiggestSanctum() != null && getNomios() != null) {
                    return 10f
                }
            }
            return 0f
        }

        override fun rollEvent(intel: HostileActivityEventIntel, stage: EventStageData) {
//		if (true) return;

            val market: MarketAPI = getBiggestSanctum() ?: return
            val nomios = getNomios() ?: return

            val data = HAERandomEventData(this, stage)
            data.custom = market
            stage.rollData = data
            intel.sendUpdateIfPlayerHasIntel(data, false)
        }

        override fun fireEvent(intel: HostileActivityEventIntel?, stage: EventStageData): Boolean {
            if (stage.rollData !is HAERandomEventData) return false
            val data = stage.rollData as HAERandomEventData
            val market = data.custom as? MarketAPI? ?: return false

            if (!market.isInEconomy) return false

            val from: MarketAPI = pickSourceMarket() ?: return false
            val system = market.starSystem ?: return false

            return createRaid(from, market, system, stage, org.lazywizard.lazylib.MathUtils.getRandom())
        }

        fun createRaid(
            source: MarketAPI,
            target: MarketAPI?,
            system: StarSystemAPI?,
            stage: EventStageData?,
            random: Random
        ): Boolean {
            val params = GenericRaidParams(Random(random.nextLong()), true)

            params.makeFleetsHostile = false // will be made hostile when they arrive, not before

            params.factionId = source.factionId
            params.source = source

            params.prepDays = 14f + random.nextFloat() * 14f
            params.payloadDays = 27f + 7f * random.nextFloat()

            params.raidParams.where = system
            params.raidParams.type = FGRaidType.SEQUENTIAL
            params.raidParams.tryToCaptureObjectives = false
            params.raidParams.allowedTargets.add(target)
            params.raidParams.allowNonHostileTargets = true

            params.style = FleetStyle.STANDARD


            // standard Askonia fleet size multiplier with no shortages/issues is a bit over 230%
            val fleetSizeMult = source.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(0f)

            val f = intel.getMarketPresenceFactor(system)

            var totalDifficulty = (fleetSizeMult * 15f * (f + 1f))

            if (totalDifficulty < 15) {
                return false
            }
            if (totalDifficulty > 100) {
                totalDifficulty = 100f
            }

            totalDifficulty -= 10f

            params.fleetSizes.add(10)

            while (totalDifficulty > 0) {
                val min = 6
                val max = 10


                //int diff = Math.round(StarSystemGenerator.getNormalRandom(random, min, max));
                val diff = min + random.nextInt(max - min + 1)

                params.fleetSizes.add(diff)
                totalDifficulty -= diff.toFloat()
            }


            val punex = NSICryosanctumRaidFGI(params)
            punex.setListener(this)
            Global.getSector().intelManager.addIntel(punex)

            Global.getSector().memoryWithoutUpdate["\$NSICryosanctumRaidOccured"] = true


//		GenericRaidFGI raid = new GenericRaidFGI(params);
//		raid.setListener(this);
//		Global.getSector().getIntelManager().addIntel(raid);
            return true
        }

        fun pickSourceMarket(): MarketAPI? {
            return Global.getSector().getFaction(Factions.PIRATES).getFactionMarkets().randomOrNull()
        }

        override fun reportFGIAborted(intel: FleetGroupIntel?) {
            Global.getSector().memoryWithoutUpdate["\$NSI_hasCryosanctumBuff"] = true
        }

        override fun getNameForThreatList(first: Boolean): String? {
            return "Protests"
        }

        override fun getNameColorForThreatList(): Color? {
            return getNomios()?.faction?.baseUIColor ?: Misc.getNegativeHighlightColor()
        }
    }

    class CrisisCause(intel: HostileActivityEventIntel): BaseHostileActivityCause2(intel) {

        override fun shouldShow(): Boolean {
            return progress > 0
        }

        override fun getDesc(): String? {
            return "Cryosanctum Protests"
        }

        override fun getTooltip(): TooltipCreator {
            return object : BaseFactorTooltip() {
                override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any?) {
                    val biggest: MarketAPI = getBiggestSanctum() ?: return
                    tooltip.addPara(
                        "Protests have erupted against your 'irresponsible' construction and management of a Cryosanctum on %s, " +
                        "with calls to dismantle your sanctum and leave its practice to \"%s\".",
                        10f,
                        Misc.getHighlightColor(),
                        "${biggest.name}", "responsible parties"
                    )
                    tooltip.addPara(
                        "Intelligence heavily suggests the protests to be sponsored by %s, having to deal with direct competition for the first time in ages.",
                        10f,
                        getNomios()?.faction?.baseUIColor ?: Misc.getHighlightColor(),
                        "Nomios"
                    )
                }
            }
        }

        override fun getMagnitudeContribution(system: StarSystemAPI?): Float {
            if (!shouldShow()) return 0f
            val largest = getBiggestSanctum() ?: return 0f
            if (largest.starSystem != system) return 0f
            return 5f
        }

        override fun getProgress(): Int {
            if (getNomios() == null) return 0
            if (Global.getSector().memoryWithoutUpdate.getBoolean("\$NSICryosanctumRaidOccured")) return 0
            return if (getBiggestSanctum() == null) 0 else 5
        }
    }

}