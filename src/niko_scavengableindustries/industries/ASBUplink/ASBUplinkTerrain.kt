package niko_scavengableindustries.industries.ASBUplink

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BattleAPI
import com.fs.starfarer.api.campaign.CampaignEngineLayers
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.TerrainAIFlags
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.Industries
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.campaign.ids.Terrain
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain
import com.fs.starfarer.api.impl.campaign.terrain.CRLossPerSecondBuff
import com.fs.starfarer.api.impl.campaign.terrain.PeakPerformanceBuff
import com.fs.starfarer.api.impl.campaign.terrain.RingRenderer
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import niko_scavengableindustries.utils.MathUtils.prob
import niko_scavengableindustries.utils.StringUtils.toPercent
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import org.magiclib.kotlin.applyDamage
import java.awt.Color
import java.util.*
import kotlin.math.max

class ASBUplinkTerrain: BaseRingTerrain() {

    companion object {
        fun getBatteries(market: MarketAPI) = market.industries.find { it.spec.hasTag(Industries.TAG_GROUNDDEFENSES) }
        fun getSpec(market: MarketAPI): ASBUplinkIndustrySpec = IND_IDS_TO_SPEC[getBatteries(market)?.spec?.id] ?: IND_IDS_TO_SPEC[Industries.GROUNDDEFENSES]!!

        val IND_IDS_TO_SPEC = mapOf(
            Pair(Industries.GROUNDDEFENSES, ASBUplinkIndustrySpec(
                Industries.GROUNDDEFENSES,
                700f,
                damageCoeff = 1f
            )),
            Pair(Industries.HEAVYBATTERIES, ASBUplinkIndustrySpec(
                Industries.HEAVYBATTERIES,
                1000f,
                damageCoeff = 1.5f
            ))
        )

        const val IMPROVED_RANGE_MULT = 1.5f
        const val ALPHA_DAMAGE_MULT = 1.2f

        const val BASE_TIMEOUT = 0.3f

        const val MIN_PROFILE_DEFAULT = 300f
        const val MAX_PROFILE_DEFAULT = 800f

        const val BASE_CR_LOSS = 0.25f

        val BOMBARD_COLOR = Color(255, 165, 100, 255)
    }

    data class ASBUplinkIndustrySpec(
        val indId: String,
        val baseRange: Float,
        val damageCoeff: Float,
        val minProfile: Float = MIN_PROFILE_DEFAULT,
        val maxProfile: Float = MAX_PROFILE_DEFAULT,
        /// If a fleet is going faster than this, accuracy decreases lineraly up to [maxBurnForFalloff].
        val minBurnForFalloff: Float = 12f,
        /// If a fleet is at or above this speed, accuracy is completely reduced by [speedFalloffMult].
        val maxBurnForFalloff: Float = 18f,
        val speedFalloffMult: Float = 0.2f
    )

    class ASBUplinkTerrainParams(
        bandWidthInEngine: Float,
        middleRadius: Float,
        relatedEntity: SectorEntityToken,
        name: String,
        val industry: ASBUplink
    ): RingParams(bandWidthInEngine, middleRadius, relatedEntity, name)

    fun getCastedParams(): ASBUplinkTerrainParams = (params as ASBUplinkTerrainParams)

    val vfxTimeouts = HashMap<CampaignFleetAPI, Float>()

    override fun applyEffect(entity: SectorEntityToken?, days: Float) {
        super.applyEffect(entity, days)

        if (entity !is CampaignFleetAPI) return
        val fleet = entity as CampaignFleetAPI // explicit cast to change name
        if (!shouldApplyEffect(fleet)) return

        val shortageMult = getCastedParams().industry.getShortagesForTerrain()
        val finalDays = days * shortageMult

        barrage(fleet, finalDays)
    }

    private fun barrage(fleet: CampaignFleetAPI, days: Float) {
        val accuracy = getAccuracy(fleet)
        val indParams: ASBUplinkIndustrySpec = getIndustryParams()
        if (fleet.battle == null) { // op in battles cause theyre standing still. do vfx anyway
            var baseMult = (indParams.damageCoeff) * 6f
            if (getCastedParams().industry.aiCoreId == Commodities.ALPHA_CORE) {
                baseMult += (1 - ALPHA_DAMAGE_MULT)
            }

            // CR loss and peak time reduction
            for (member in fleet.fleetData.membersListCopy) {
                val recoveryRate = member.stats.baseCRRecoveryRatePercentPerDay.getModifiedValue()
                val lossRate = member.stats.baseCRRecoveryRatePercentPerDay.baseValue

                val adjustedLossMult: Float = (baseMult * accuracy * BASE_CR_LOSS)
                var loss: Float = (-1f * recoveryRate + -1f * lossRate * adjustedLossMult) * days * 0.01f
                val curr = member.repairTracker.baseCR
                if (loss > curr) loss = curr
                member.repairTracker.applyCREvent(loss, "${getMarket().id}_${spec.id}", "Flak Fire")
                if (accuracy > 0.03f && prob(25 * days)) {
                    // strong hit, does damage
                    var hitsLeft = 1f
                    while (hitsLeft-- > 0f) {
                        val hitStrength = member.stats.armorBonus.computeEffective(member.hullSpec.armorRating)
                        member.status.applyDamage(hitStrength * baseMult)
                    }
                }

                // needs to be applied when resistance is 0 to immediately cancel out the debuffs (by setting them to 0)
                val peakFraction = 1f / max(1.3333f, 1f + baseMult * accuracy)
                var peakLost = 1f - peakFraction
                val degradationMult: Float = 1f + (baseMult * accuracy) / 2f
                member.buffManager.addBuffOnlyUpdateStat(
                    PeakPerformanceBuff(
                        "${getMarket().id}_${spec.id}_1",
                        1f - peakLost,
                        0.1f
                    )
                )
                member.buffManager.addBuffOnlyUpdateStat(
                    CRLossPerSecondBuff(
                        "${getMarket().id}_${spec.id}_2",
                        degradationMult,
                        0.1f
                    )
                )
            }
        }

        if (vfxTimeouts[fleet] == null) {
            doVfx(fleet, accuracy)

            adjustTimeout(fleet, (BASE_TIMEOUT / indParams.damageCoeff) * MathUtils.getRandomNumberInRange(0.3f, 1.7f))
        }
    }

    private fun doVfx(fleet: CampaignFleetAPI, accuracy: Float) {
        val radiusMult = 1 + (5f - (5f * accuracy))
        val radius = fleet.radius * (radiusMult)

        val glowSize = 300f * MathUtils.getRandomNumberInRange(0.7f, 1.3f)

        val loc = MathUtils.getRandomPointInCircle(fleet.location, radius)
        Misc.addHitGlow(fleet.containingLocation, loc, Misc.ZERO, glowSize, 2f + MathUtils.getRandomNumberInRange(-0.3f, 0.3f), BOMBARD_COLOR)
        Global.getSoundPlayer().playSound("mine_explosion", 1f, 0.5f, loc, Misc.ZERO)
    }

    override fun advance(amount: Float) {
        super.advance(amount)

        val days = Misc.getDays(amount)
        val shortageMult = getCastedParams().industry.getShortagesForTerrain()
        val finalDays = days * shortageMult

        if (vfxTimeouts.isEmpty()) return
        for (entry in vfxTimeouts.entries.toSet()) {
            if (!entry.key.isAlive) {
                vfxTimeouts -= entry.key
                continue
            }
            vfxTimeouts[entry.key] = entry.value - finalDays
            if (vfxTimeouts[entry.key]!! <= 0f) {
                vfxTimeouts -= entry.key
            }
        }
    }

    fun adjustTimeout(fleet: CampaignFleetAPI, days: Float) {
        if (vfxTimeouts[fleet] == null) vfxTimeouts[fleet] = 0f

        vfxTimeouts[fleet] = vfxTimeouts[fleet]!! + days
    }

    private fun shouldApplyEffect(fleet: CampaignFleetAPI): Boolean {
        val market = getMarket()
        return fleet.faction.isHostileTo(market.faction)
    }

    /// Is translated into both the accuracy of the hit particles and the rate of CR degradation. 0-1.
    private fun getAccuracy(fleet: CampaignFleetAPI): Float {
        val indParams = getIndustryParams()
        val profile = fleet.detectedRangeMod.computeEffective(fleet.sensorProfile)
        val burn = fleet.currBurnLevel
        val effectiveProfile = (profile - indParams.minProfile)
        var accuracy = (effectiveProfile / (indParams.maxProfile - indParams.minProfile)).coerceAtLeast(0f).coerceAtMost(1f)

        val effectiveBurn = (burn - indParams.minBurnForFalloff).coerceAtLeast(0f)
        val limit = (indParams.maxBurnForFalloff - indParams.minBurnForFalloff)
        val burnMult = (1 - (effectiveBurn / limit)).coerceAtLeast(0.2f) // tODO - use speedFalloffMult, couldnt figure out the math first time

        accuracy *= burnMult

        return accuracy.coerceAtLeast(0.01f)
    }

    fun getIndustryParams(): ASBUplinkIndustrySpec {
        return getSpec(getMarket())
    }

    fun getMarket(): MarketAPI {
        val ourParams = getCastedParams()
        return ourParams.industry.market
    }

    override fun hasAIFlag(flag: Any?, fleet: CampaignFleetAPI?): Boolean {
        if (fleet == null) return super.hasAIFlag(flag, fleet)
        val avg = (fleet.fleetData.membersListCopy.sumOf { it.repairTracker.cr.toDouble() } * fleet.fleetData.membersListCopy.size).toFloat()
        if (avg <= 0.3f && flag == TerrainAIFlags.CR_DRAIN) {
            return true
        }
        /*if (fleet != null && !shouldApplyEffect(fleet)) return false

        return (flag == TerrainAIFlags.CR_DRAIN || super.hasAIFlag(flag, fleet))*/
            return super.hasAIFlag(flag, fleet)
    }

    override fun getNameForTooltip(): String {
        val baseName = getBaseName()
        var alignmentString = "Friendly"
        if (getMarket().faction.isHostileTo(Global.getSector().playerFaction)) alignmentString = "Hostile"

        return "$baseName ($alignmentString)"
    }

    fun getBaseName(): String {
        val market = getMarket()
        val baseName = "${market.name} ${getBatteries(market)?.currentName}"
        return baseName
    }

    override fun getTerrainName(): String {
        return nameForTooltip
    }
    val UID = Misc.genUID()

    override fun getTerrainId(): String? {
        return UID
    }

    override fun getEffectCategory(): String? {
        return null
    }

    override fun canPlayerHoldStationIn(): Boolean {
        return false
    }

    override fun getNameColor(): Color? {
        val baseColor = getMarket().faction.baseUIColor
        if (getMarket().faction.isHostileTo(Factions.PLAYER)) {
            val special = Misc.getNegativeHighlightColor()
            val base = baseColor
            //bad = Color.red;
            return Misc.interpolateColor(base, special, Global.getSector().campaignUI.sharedFader.brightness * 1f)
        }

        return baseColor
    }

    override fun hasTooltip(): Boolean {
        return true
    }

    override fun createTooltip(tooltip: TooltipMakerAPI?, expanded: Boolean) {
        if (tooltip == null) return
        val indParams = getIndustryParams()

        val codex = Global.getSettings().isShowingCodex
        val pad = 10f
        val small = 5f
        val gray = Misc.getGrayColor()
        val highlight = Misc.getHighlightColor()
        val fuel = Global.getSettings().getColor("progressBarFuelColor")
        val bad = Misc.getNegativeHighlightColor()

        val baseName = getBaseName()
        tooltip.addTitle(baseName)

        var nextPad = pad
        if (expanded) {
            tooltip.addSectionHeading("Travel", Alignment.MID, pad)
            nextPad = small
        }

        val playerFleet = Global.getSector().playerFleet
        val hostile = getMarket().faction.isHostileTo(Global.getSector().playerFaction)
        val market = getMarket()
        tooltip.addPara(
            "Your fleet is in range of %s %s, which is capable of %s.",
            pad,
            market.faction.baseUIColor,
            "${market.name}'s", "long-range flak system", "striking fleets in near orbit of the colony"
        ).setHighlightColors(
            market.faction.baseUIColor,
            Misc.getHighlightColor(),
            Misc.getHighlightColor()
        )
        val ind = getBatteries(market)
        tooltip.addPara(
            "Long-ranged flak barrage is inaccurate, moreso if certain %s are taken. The %s on %s become progressively more inaccurate " +
                    "as %s falls from %s to %s, where it becomes nearly impossible to be hit. The same is true as burn level rises from %s to %s, " +
                    "where above the maximum being hit is far more difficult.",
            nextPad,
            Misc.getHighlightColor(),
            "countermeasures", "${ind?.currentName}", baseName, "sensor profile", "${indParams.maxProfile.toInt()}", "${indParams.minProfile.toInt()}",
            "${indParams.minBurnForFalloff.toInt()}", "${indParams.maxBurnForFalloff.toInt()}"
        ).setHighlightColors(
            Misc.getHighlightColor(), market.faction.baseUIColor, market.faction.baseUIColor, Misc.getHighlightColor(), Misc.getHighlightColor(), Misc.getHighlightColor(), Misc.getHighlightColor()
        )

        if (hostile) {
            tooltip.addPara(
                "Your fleet is %s to the forces controlling %s, meaning you are in danger of flak barrage.",
                nextPad,
                Misc.getNegativeHighlightColor(),
                "hostile", market.name
            ).setHighlightColors(
                Misc.getNegativeHighlightColor(),
                market.faction.baseUIColor,
            )

            val accuracy = getAccuracy(playerFleet)
            tooltip.addPara(
                "The current accuracy ratio, when targeting your fleet, is %s.",
                nextPad,
                Misc.getHighlightColor(),
                toPercent(accuracy)
            )

        }
        tooltip.addSectionHeading("Combat", Alignment.MID, nextPad)

        tooltip.addPara(
            "The flak cannons are likely to %s, resulting in %s being fired towards whichever side %s is hostile to.",
            nextPad,
            Misc.getHighlightColor(),
            "interfere with combat", "massive flak shots", "${market.name}"
        ).setHighlightColors(
            Misc.getHighlightColor(),
            Misc.getHighlightColor(),
            market.faction.baseUIColor
        )
    }

    override fun containsPoint(point: Vector2f?, radius: Float): Boolean {
        val indParams = getIndustryParams()
        val dist = MathUtils.getDistance(getMarket().primaryEntity, point)
        return dist <= getRange()
    }

    @Transient
    var rr: RingRenderer? = null
    override fun renderOnMap(factor: Float, alphaMult: Float) {
        if (rr == null) {
            rr = RingRenderer("systemMap", "map_asteroid_belt")
        }
        rr!!.render(
            entity.location,
            getRange() - 100f,
            getRange() + 100f,
            getMarket().faction.color,
            false, factor, alphaMult
        )
    }

    override fun renderOnRadar(radarCenter: Vector2f?, factor: Float, alphaMult: Float) {
        if (radarCenter == null) return

        GL11.glPushMatrix()
        GL11.glTranslatef(-radarCenter.x * factor, -radarCenter.y * factor, 0f)
        renderOnMap(factor, alphaMult)
        GL11.glPopMatrix()

        /*if (params == null) return
        if (!castedParams.parent.renderTerrain) return
        if (rr == null) {
            rr = RingRenderer("systemMap", "map_asteroid_belt")
        }
        rr!!.render(
            entity.location,
            castedParams.parent.getMaxTargettingRange() - 500f,
            castedParams.parent.getMaxTargettingRange(),
            placeholderColor,
            false, factor, alphaMult
        )*/
    }

    override fun getActiveLayers(): EnumSet<CampaignEngineLayers?>? {
        return EnumSet.of(CampaignEngineLayers.TERRAIN_3)
    }

    override fun render(layer: CampaignEngineLayers?, v: ViewportAPI?) {
        super.render(layer, v)

        if (rr == null) {
            rr = RingRenderer("systemMap", "map_asteroid_belt")
        }
        rr!!.render(
            entity.location,
            getRange(),
            getRange() + 70f,
            getMarket().faction.color,
            false, 1f, 0.5f
        )
    }

    fun getRange(): Float {
        val indParams = getIndustryParams()
        var range = indParams.baseRange
        if (getCastedParams().industry.isImproved) range *= IMPROVED_RANGE_MULT
        return range
    }

    override fun stacksWithSelf(): Boolean {
        return true
    }

    override fun getRenderRange(): Float {
        return 99999f
    }

}