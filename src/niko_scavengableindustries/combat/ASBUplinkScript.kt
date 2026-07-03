package niko_scavengableindustries.combat

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.CollisionClass
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.MissileAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.loading.DamagingExplosionSpec
import com.fs.starfarer.api.loading.ProjectileSpecAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import niko_scavengableindustries.utils.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import java.awt.Color

class ASBUplinkScript(val isPlayerSide: Boolean): BaseEveryFrameCombatPlugin() {

    companion object {
        fun getRandSourceLoc(playerSide: Boolean): Vector2f {
            val engine = Global.getCombatEngine()
            val height = engine.mapHeight / 2f
            val middle = if (playerSide) -height else height
            val width = engine.mapWidth / 2f

            return (Vector2f(org.lazywizard.lazylib.MathUtils.getRandomNumberInRange(-width, width), middle))
        }

        fun fireShot(area: Area, target: Vector2f, playerSide: Boolean): CombatEntityAPI? {
            // TODO - make membevr function later
            val engine = Global.getCombatEngine()
            val sourceLoc = getRandSourceLoc(playerSide)
            val angle = VectorUtils.getAngleStrict(sourceLoc, target)
            val proj = engine.spawnProjectile(null, null, area.weaponId, sourceLoc, angle, Misc.ZERO)
            proj.owner = if (playerSide) 0 else 1
            return proj
        }

        val GOOD_WARNING_COLOR = Color(40, 120, 255)
        val BAD_WARNING_COLOR = Color(255, 90, 50)

        const val PROX_DETONATION = 250f
        const val INACCURACY_MULT = 10f
        const val THREAT_INDICATOR_WEAPON_ID = "NSI_flakThreatIndicator"
    }

    init {
        Global.getSettings().loadTexture("graphics/fx/NSI_flakWarning.png")
    }

    enum class Area(val weaponId: String) {
        OVER("NSI_flakMiss"),
        UNDER("NSI_flakMiss"),
        DIRECT("NSI_flakCannon");
    }

    val engine = Global.getCombatEngine()
    val owner = if (isPlayerSide) 0 else 1

    val fireInterval = IntervalUtil(23.4f, 53.2f)
    override fun advance(amount: Float, events: List<InputEventAPI?>?) {
        super.advance(amount, events)

        if (engine.isPaused) return
        val realAmount = amount * engine.timeMult.modified

        fireInterval.advance(realAmount)
        if (fireInterval.intervalElapsed()) {
            tryFiring()
        }
        handleShots(amount)
    }

    class ShotData(
        val proj: MissileAPI,
        val target: Vector2f,
        var warningRotation: Float = org.lazywizard.lazylib.MathUtils.getRandomNumberInRange(0f, 360f),
    ) {
        var mine: MissileAPI? = null
        var lastDist: Float = Float.MAX_VALUE
    }

    val shots = HashSet<ShotData>()
    private fun handleShots(amount: Float) {
        for (entry in shots.toList()) {
            val shot = entry.proj
            val target = entry.target
            if (!engine.isEntityInPlay(shot)) {
                if (entry.mine != null) {
                    engine.removeEntity(entry.mine)
                }
                shots -= entry
                continue
            }
            val dist = org.lazywizard.lazylib.MathUtils.getDistance(
                shot,
                target
            )
            if (dist <= PROX_DETONATION) {
                shot.explode()
                engine.removeEntity(shot)
                shots -= entry
                continue
            }
            if (dist > entry.lastDist) {
                shot.explode()
                engine.removeEntity(shot)
                shots -= entry
                continue
            }
            entry.lastDist = dist
            repositionThreadIndicator(entry)

            entry.warningRotation += (20f * amount)
            MagicRender.singleframe(
                Global.getSettings().getSprite("graphics/fx/NSI_flakWarning.png"),
                target,
                Vector2f(shot.spec.explosionRadius, shot.spec.explosionRadius),
                entry.warningRotation,
                if (isPlayerSide) GOOD_WARNING_COLOR else BAD_WARNING_COLOR,
                true,
                CombatEngineLayers.UNDER_SHIPS_LAYER
            )
        }
    }

    fun repositionThreadIndicator(data: ShotData) {
        if (data.mine != null) {
            engine.removeEntity(data.mine)
        }
        data.mine = createThreatIndicator(data) as MissileAPI
    }

    fun createThreatIndicator(data: ShotData): CombatEntityAPI {
        val threatIndicator = engine.spawnProjectile(
            null,
            null,
            THREAT_INDICATOR_WEAPON_ID,
            data.target,
            0f,
            null
        )
        if (threatIndicator is MissileAPI) {
            threatIndicator.untilMineExplosion = 0.1f
            threatIndicator.damage.damage = data.proj.damage.damage
            threatIndicator.damage.type = data.proj.damageType
        }
        threatIndicator.owner = 100
        return threatIndicator
    }

    private fun tryFiring() {
        val target = getTarget() ?: return

        val proj = fireShot(Area.DIRECT, target, isPlayerSide) as MissileAPI
        shots += ShotData(proj, target)
    }

    fun getTarget(): Vector2f? {
        val manager = engine.getFleetManager(if (owner == 1) 0 else 1)
        for (deployed in manager.deployedCopyDFM.shuffled()) {
            val ship = deployed.ship ?: continue
            if (ship.isHulk || !ship.isAlive || ship.isFighter || ship.isDrone || ship.isStationModule) continue
            // target located
            val targetLoc = org.lazywizard.lazylib.MathUtils.getRandomPointInCircle(
                ship.location,
                ship.collisionRadius * INACCURACY_MULT
            )
            return targetLoc

        }
        return null
    }

}
