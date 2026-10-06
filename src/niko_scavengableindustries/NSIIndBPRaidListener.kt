package niko_scavengableindustries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.listeners.GroundRaidObjectivesListener
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.impl.campaign.graid.BaseGroundRaidObjectivePluginImpl
import com.fs.starfarer.api.impl.campaign.graid.GroundRaidObjectivePlugin
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.Industries
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import org.magiclib.kotlin.getFactionMarkets
import java.util.*

class NSIIndBPRaidListener: GroundRaidObjectivesListener {
    companion object {
        const val RAID_XP = 1000

        fun getIndFromRaid(market: MarketAPI): String? {
            val player = Global.getSector().playerFaction
            val randExisting = market.industries.filter { market.faction.knownIndustries.contains(it.spec.id) && NSISettings.industrySpecs.contains(it.spec.id) && !player.knownIndustries.contains(it.spec.id) }.randomOrNull()
            if (randExisting != null) {
                return randExisting.spec.id
            }
            val known = market.faction.knownIndustries
            var pickFrom = known.filter { !player.knownIndustries.contains(it) }
            if (pickFrom.isEmpty()) pickFrom = known.toList()

            return pickFrom.randomOrNull()
        }
    }

    override fun modifyRaidObjectives(
        market: MarketAPI?,
        entity: SectorEntityToken?,
        objectives: MutableList<GroundRaidObjectivePlugin>?,
        type: MarketCMD.RaidType?,
        marineTokens: Int,
        priority: Int
    ) {
        if (market == null || objectives == null) return
        if (type != MarketCMD.RaidType.VALUABLE) return
        if (objectives.any { it.id == "NSIIndBPRaid" }) return

        val largest = market.size >= market.faction.getFactionMarkets().maxOf { it.size }
        if (!largest) return

        val plugin = IndRaidObjPlugin(market, "NSIIndBPRaid")
        objectives.add(plugin)
    }

    override fun reportRaidObjectivesAchieved(
        data: GroundRaidObjectivesListener.RaidResultData?,
        dialog: InteractionDialogAPI?,
        memoryMap: Map<String?, MemoryAPI?>?
    ) {
        return
    }

    class IndRaidObjPlugin(market: MarketAPI, id: String): BaseGroundRaidObjectivePluginImpl(market, id) {

        init {
            this.setSource(market.industries.find { it.spec.hasTag(Industries.TAG_POPULATION) })
            this.setMarinesRequired(this.marinesRequired * 2)
        }

        override fun setSource(source: Industry?) {
            super.setSource(source)
            val level = dangerLevel
            var marines = level.marineTokens
            if (source != null) {
                marines = source.adjustMarineTokensToRaidItem(id, null, marines)
            }
            setMarinesRequired(marines)
        }

        override fun getQuantity(marines: Int): Float {
            return 1f
        }

        override fun getValue(marines: Int): Int {
            return 0
        }

        override fun getQuantitySortValue(): Float {
            return QUANTITY_SORT_TIER_2
        }

        override fun getProjectedCreditsValue(): Int {
            return 0
        }

        override fun getValueString(marines: Int): String {
            return ""
        }

        override fun getName(): String = "Industry Blueprints"

        override fun hasTooltip(): Boolean {
            return true
        }

        override fun createTooltip(tooltip: TooltipMakerAPI?, expanded: Boolean) {
            super.createTooltip(tooltip, expanded)

            if (tooltip == null) return

            tooltip.addPara(
                "Being a capital of ${market.faction.displayName}, ${market.name} houses plentiful industry blueprints.",
                10f
            ).color = Misc.getPositiveHighlightColor()

            tooltip.addPara(
                "Your marines will prioritize blueprints %s and %s.",
                10f,
                Misc.getHighlightColor(),
                "present on the colony", "unknown by your faction"
            )
        }

        override fun getIconName(): String? {
            return Global.getSettings().getCommoditySpec(Commodities.BLUEPRINTS).iconName
        }

        override fun getSource(): Industry? {
            return market.industries.find { it.spec.hasTag(Industries.TAG_POPULATION) }
        }

        override fun getDangerLevel(): MarketCMD.RaidDangerLevel {
            return MarketCMD.RaidDangerLevel.EXTREME
        }

        override fun performRaid(
            loot: CargoAPI?,
            random: Random?,
            lootMult: Float,
            text: TextPanelAPI?
        ): Int {
            if (loot == null) return 0

            val rand = getIndFromRaid(market)
            if (rand != null) {
                loot.addSpecial(
                    SpecialItemData(
                        "NSI_IndustryBPItem",
                        rand
                    ),
                    1f
                )
            }

            return RAID_XP
        }
    }
}