package niko_scavengableindustries.combat

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BattleAPI
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.input.InputEventAPI
import niko_scavengableindustries.industries.ASBUplink.ASBUplink
import niko_scavengableindustries.industries.ASBUplink.ASBUplinkTerrain

class ASBUplinkCombatAdder: BaseEveryFrameCombatPlugin() {
    val engine = Global.getCombatEngine()

    override fun advance(amount: Float, events: List<InputEventAPI?>?) {
        super.advance(amount, events)
        if (engine == null) return
        if (Global.getCurrentState() != GameState.COMBAT) return engine.removePlugin(this)
        if (engine.isMission) return engine.removePlugin(this)

        attemptToAddASBs()

        engine.removePlugin(this)
    }

    private fun attemptToAddASBs() {
        val playerFleet = Global.getSector()?.playerFleet ?: return
        val playerLocation = playerFleet.containingLocation ?: return

        val asbTerrain = playerLocation.terrainCopy.filter { it.plugin is ASBUplinkTerrain }.map { it.plugin } as List<ASBUplinkTerrain>
        if (asbTerrain.isEmpty()) return

        val containsList = asbTerrain.filter { it.containsEntity(playerFleet) }
        val contains = HashMap<ASBUplinkTerrain, ASBUplink>()
        for (terrain in containsList) {
            val market = terrain.getMarket() ?: continue
            val ind = market.getIndustry("NSI_ASBUplink")

            contains[terrain] = ind as ASBUplink
        }
        if (contains.isEmpty()) return

        addASBs(contains, playerFleet.battle)
    }

    private fun addASBs(contains: Map<ASBUplinkTerrain, ASBUplink>, battle: BattleAPI) {
        for (entry in contains) {
            val terrain = entry.key
            val ind = entry.value
            val dummyFleet = FleetFactory.createEmptyFleet(terrain.getMarket().faction.id, FleetTypes.PATROL_LARGE, null)
            val side = battle.pickSide(dummyFleet)
            val playerSide = battle.pickSide(Global.getSector().playerFleet)
            val isPlayerSide = side == playerSide
            dummyFleet.despawn()
            if (side == BattleAPI.BattleSide.NO_JOIN) continue

            engine.addPlugin(ASBUplinkScript(isPlayerSide, ind, terrain))
        }
    }
}