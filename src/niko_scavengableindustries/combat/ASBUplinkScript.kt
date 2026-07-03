package niko_scavengableindustries.combat

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.CollisionClass
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamageType
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.MissileAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.loading.DamagingExplosionSpec
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import niko_scavengableindustries.industries.ASBUplink.ASBUplink
import niko_scavengableindustries.industries.ASBUplink.ASBUplinkTerrain
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class ASBUplinkScript(val isPlayerSide: Boolean, val ind: ASBUplink, val terrain: ASBUplinkTerrain): BaseEveryFrameCombatPlugin() {

    companion object {

        val GOOD_WARNING_COLOR = Color(90, 200, 255)
        val BAD_WARNING_COLOR = Color(255, 90, 50)

        const val PROX_DETONATION = 50f
        const val INACCURACY_INCR = 1500f
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

    val fireInterval = IntervalUtil(5.4f, 12.2f)

    val dummyFighter: ShipAPI
    init {
        val fleetManager = engine.getFleetManager(owner)
        val fleetMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "talon_Interceptor")
        val originalValue = fleetManager.isSuppressDeploymentMessages
        fleetManager.isSuppressDeploymentMessages = true
        fleetMember.shipName = "Flak Barrage"
        val dummyShip = fleetManager.spawnFleetMember(fleetMember, Vector2f(999999f, 999999f), 5f, 0f)
        dummyShip.name = "Flak Barrage"
        dummyShip.collisionClass = CollisionClass.NONE
        dummyShip.shipAI = null
        dummyShip.owner = owner
        dummyShip.mutableStats.hullDamageTakenMult.modifyMult("MCTE_minefieldEffect", 0f)
        dummyShip.alphaMult = 0f
        fleetManager.isSuppressDeploymentMessages = originalValue

        dummyFighter = dummyShip
    }

    fun getRandSourceLoc(): Vector2f {
        val engine = Global.getCombatEngine()
        val height = engine.mapHeight * 0.75f
        val middle = if (isPlayerSide) -height else height
        val width = engine.mapWidth / 2f

        return (Vector2f(org.lazywizard.lazylib.MathUtils.getRandomNumberInRange(-width, width), middle))
    }

    fun fireShot(area: Area, target: Vector2f): CombatEntityAPI? {
        // TODO - make membevr function later
        val sourceLoc = getRandSourceLoc()
        val angle = VectorUtils.getAngleStrict(sourceLoc, target)
        val proj = engine.spawnProjectile(null, null, area.weaponId, sourceLoc, angle, Misc.ZERO)
        proj.owner = if (isPlayerSide) 0 else 1
        return proj
    }

    fun getExplosionSpec(): DamagingExplosionSpec {
        var damage = 3000f
        var minDamage = 1000f
        if (ind.aiCoreId == Commodities.ALPHA_CORE) {
            damage *= ASBUplinkTerrain.ALPHA_DAMAGE_MULT
            minDamage *= ASBUplinkTerrain.ALPHA_DAMAGE_MULT
        }
        damage *= terrain.getIndustryParams().damageCoeff
        minDamage *= terrain.getIndustryParams().damageCoeff
        val spec = DamagingExplosionSpec(
            0.1f,
            1000f,
            600f,
            damage,
            minDamage,
            CollisionClass.PROJECTILE_NO_FF,
            CollisionClass.PROJECTILE_FIGHTER,
            3f,
            4f,
            3f,
            100,
            Color(255,200,100,255),
            Color(255,120,70,255)
        )
        spec.isUseDetailedExplosion = true
        spec.soundSetId = "mine_explosion"
        spec.damageType = DamageType.HIGH_EXPLOSIVE
        return spec
    }

    fun doExplosion(loc: Vector2f, owner: Int, ship: ShipAPI) {
        val expl = Global.getCombatEngine().spawnDamagingExplosion(
            getExplosionSpec(),
            ship,
            loc
        )
        expl.owner = owner

        var fragmentsLeft = 500f
        while (fragmentsLeft-- > 0f) {
            val randStart = MathUtils.getRandomPointOnCircumference(
                loc,
                50f
            )
            val angle = VectorUtils.getAngle(
                loc,
                randStart
            )
            val proj = Global.getCombatEngine().spawnProjectile(null, null, "shredder", randStart, angle, Misc.ZERO)
            proj.velocity.scale(3f)
            proj.owner = owner
            proj.collisionClass = if (proj.collisionClass == CollisionClass.RAY) CollisionClass.RAY_FIGHTER else CollisionClass.PROJECTILE_NO_FF
        }
    }

    override fun advance(amount: Float, events: List<InputEventAPI?>?) {
        super.advance(amount, events)

        val data = if (isPlayerSide) "Friendly ordnance inbound" else "Hostile ordnance inbound"
        engine.maintainStatusForPlayerShip(
            this.toString(),
            "graphics/icons/hullsys/lidar_barrage.png",
            "Flak barrage - ${ind.market.name}",
            data,
            !isPlayerSide
        )

        if (engine.isPaused) return
        val realAmount = amount * engine.timeMult.modified * ind.getShortagesForTerrain()

        fireInterval.advance(realAmount)
        if (fireInterval.intervalElapsed()) {
            tryFiring()
        }
        handleShots(amount)
    }

    class ShotData(
        val proj: DamagingProjectileAPI,
        val target: Vector2f,
        var warningRotation: Float = org.lazywizard.lazylib.MathUtils.getRandomNumberInRange(0f, 360f),
    ) {
        var mine: MissileAPI? = null
        var lastDist: Float = Float.MAX_VALUE
        var linger = 0.5f
    }

    val shots = HashSet<ShotData>()
    private fun handleShots(amount: Float) {
        for (entry in shots.toList()) {
            val shot = entry.proj
            val target = entry.target
            if (!engine.isEntityInPlay(shot)) {
                if (shotIsExpired(entry)) {
                    entry.linger -= amount
                    if (canRemoveShot(entry)) {
                        cleanShot(entry)
                        continue
                    }
                }
            }
            val dist = org.lazywizard.lazylib.MathUtils.getDistance(
                shot,
                target
            )
            if (engine.isEntityInPlay(shot) && dist <= PROX_DETONATION) {
                doExplosion(entry.target, owner, dummyFighter)
                engine.removeEntity(shot)
                continue
            }
            if (engine.isEntityInPlay(shot) && dist > entry.lastDist) {
                doExplosion(entry.target, owner, dummyFighter)
                engine.removeEntity(shot)
            }
            repositionThreatIndicator(entry)
            if (engine.isEntityInPlay(shot)) {
                entry.lastDist = dist

                entry.warningRotation += (20f * amount)
                val spec = getExplosionSpec()
                /*MagicRender.singleframe(
                Global.getSettings().getSprite("graphics/fx/NSI_flakWarning.png"),
                target,
                Vector2f(spec.radius * 1.5f, spec.radius * 1.5f),
                entry.warningRotation,
                if (isPlayerSide) GOOD_WARNING_COLOR else BAD_WARNING_COLOR,
                true,
                CombatEngineLayers.UNDER_SHIPS_LAYER
            )*/
            }
        }
    }

    private fun canRemoveShot(entry: ShotData): Boolean {
        return entry.linger <= 0f
    }

    private fun shotIsExpired(entry: ShotData): Boolean {
        return (!engine.isEntityInPlay(entry.proj))
    }

    private fun cleanShot(shot: ShotData) {
        if (shot.mine != null) {
            engine.removeEntity(shot.mine)
        }
        if (engine.isEntityInPlay(shot.proj)) engine.removeEntity(shot.proj)
        shots -= shot
    }

    fun repositionThreatIndicator(data: ShotData) {
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
            val dist = org.lazywizard.lazylib.MathUtils.getDistance(data.proj, data.target)
            var speed = data.proj.velocity.length()
            if (speed == 0f) speed = 1f
            threatIndicator.untilMineExplosion = 0.1f
            threatIndicator.mineExplosionRange = getExplosionSpec().radius * 2f
            threatIndicator.damage.damage = getExplosionSpec().maxDamage
            threatIndicator.damage.type = data.proj.damageType
        }
        threatIndicator.owner = owner
        return threatIndicator
    }

    private fun tryFiring() {
        val target = getTarget() ?: return

        val proj = fireShot(Area.DIRECT, target) as DamagingProjectileAPI
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
                ship.collisionRadius + INACCURACY_INCR
            )
            return targetLoc

        }
        return null
    }

}
