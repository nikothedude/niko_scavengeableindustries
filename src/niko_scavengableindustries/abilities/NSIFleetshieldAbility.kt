package niko_scavengableindustries.abilities

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignEngineLayers
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.RepLevel
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.campaign.terrain.ShoveFleetScript
import com.fs.starfarer.api.util.Misc
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.util.vector.Vector2f
import org.magiclib.kotlin.addGlowyParticle
import org.magiclib.terrain.MagicAsteroidBeltTerrainPlugin
import java.awt.Color
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class NSIFleetshieldAbility: BaseToggleAbility() {

    companion object {
        const val BASE_FUEL_COST_PER_SEC = 5f
        const val DETECTABILITY_FLAT = 1000f
        const val BOUNCE_FUEL_COST = 500f
        const val MIN_FUEL = 100f
        const val BASE_REP_LOSS = -0.02f
        const val BASE_BOUNCE_COST_CD = 1f
        /**
         * Critical. Closes the hole in the shield. Always add this to arc.
         * */
        const val BUFFER = 10f
    }

    @Transient
    var sprite: SpriteAPI? = null
    @Transient
    var spriteTwo: SpriteAPI? = null

    var currArc = 0.1f
        set(value) {
            field = min(value, 360f)
        }

    var texX = 0f
    var fuelCostCooldown = 0f

    override fun getActivationText(): String {
        return "Fleetshield Engaged"
    }

    override fun getDeactivationText(): String {
        return "Fleetshield Disengaged"
    }

    override fun isUsable(): Boolean {
        return super.isUsable && fleet.cargo.fuel >= MIN_FUEL
    }

    override fun activateImpl() {
        Global.getSoundPlayer().playSound(
            "NSIshield_raise",
            1f,
            1f,
            fleet.location,
            Misc.ZERO
        )
    }

    override fun applyEffect(amount: Float, level: Float) {
        if (!isActive) return

        val mult = (currArc / 360f) * level

        fleet.fleetData.membersListCopy.forEach { it.stats.dynamic.getStat(Stats.CORONA_EFFECT_MULT).modifyMult(spec.id, 1f - mult) }
        fleet.commanderStats.dynamic.getStat(MagicAsteroidBeltTerrainPlugin.IMPACT_DAMAGE_CHANCE).modifyMult(spec.id, 1f - mult)

        fleet.detectedRangeMod.modifyFlat(spec.id, DETECTABILITY_FLAT, "Fleetshield")

        val radius = getRadius()
        val containing = fleet.containingLocation
        for (iterFleet in containing.fleets - fleet) {
            val contact = MathUtils.getPointOnCircumference(
                fleet.location,
                radius,
                VectorUtils.getAngle(fleet.location, iterFleet.location)
            )
            val dist = MathUtils.getDistance(iterFleet, contact)
            if (dist > 0f) continue
            if (!Misc.isInArc(
                fleet.facing,
                currArc,
                VectorUtils.getAngle(fleet.location, iterFleet.location)
            )) continue

            repelFleet(iterFleet)
        }

        doPassiveFuelDeduction(amount, level)
    }

    fun doPassiveFuelDeduction(amount: Float, level: Float) {
        val amnt = calcFuelCostPerSec() * amount * level
        deductFuel(amnt)
    }

    private fun deductFuel(amount: Float) {
        fleet.cargo.removeFuel(amount)
        if (fleet.cargo.fuel < MIN_FUEL) {
            deactivate()
            fleet.addFloatingText(
                "Out of fuel",
                Misc.setAlpha(entity.indicatorColor, 255),
                0.5f
            )
        }
    }

    private fun calcFuelCostPerSec(): Float {
        return BASE_FUEL_COST_PER_SEC
    }

    fun getRadius() = fleet.radius * 4f

    fun repelFleet(fleet: CampaignFleetAPI) {
        if (fleet.memoryWithoutUpdate.getBoolean("\$NSI_repelledByFleetshield")) return

        fleet.memoryWithoutUpdate.set("\$NSI_repelledByFleetshield", true, 0.01f)
        fleet.addScript(ShoveFleetScript(fleet, VectorUtils.getAngle(this.fleet.location, fleet.location), 1f))
        if (fleet.isPlayerFleet) {
            Global.getSector().campaignUI.addMessage(
                "Repelled by energy field",
                Misc.getNegativeHighlightColor()
            )
        }
        Global.getSoundPlayer().playSound("gate_explosion_fleet_impact", 2f, 1f, entity.location, Misc.ZERO)

        if (fuelCostCooldown <= 0f) {
            deductFuel(BOUNCE_FUEL_COST)
            fuelCostCooldown = BASE_BOUNCE_COST_CD
        }

        if (this.fleet.isPlayerFleet && fleet.knowsWhoPlayerIs() && !fleet.isHostileTo(this.fleet)) {
            val repParams = CustomRepImpact()
            repParams.delta = -BASE_REP_LOSS
            repParams.limit = RepLevel.INHOSPITABLE
            Global.getSector().adjustPlayerReputation(
                RepActionEnvelope(RepActions.CUSTOM, repParams, null, false),
                fleet.faction.id
            )
        }
    }

    override fun deactivateImpl() {
        cleanupImpl()
        Global.getSoundPlayer().playSound(
            "NSIshield_lower",
            1f,
            1f,
            fleet.location,
            Misc.ZERO
        )
    }

    override fun cleanupImpl() {
        fleet.fleetData.membersListCopy.forEach { it.stats.dynamic.getStat(Stats.CORONA_EFFECT_MULT).unmodify(spec.id) }
        fleet.commanderStats.dynamic.getStat(MagicAsteroidBeltTerrainPlugin.IMPACT_DAMAGE_CHANCE).unmodify(spec.id)
        fleet.detectedRangeMod.unmodify(spec.id)
    }

    override fun advance(amount: Float) {
        super.advance(amount)
        texX += amount
        if (isActive) {
            currArc += amount * 80f
        } else if (!isInProgress) {
            currArc = 0.1f
        }
        fuelCostCooldown = (fuelCostCooldown - amount).coerceAtLeast(0f)
        return
    }

    override fun getActiveLayers(): EnumSet<CampaignEngineLayers?>? {
        return EnumSet.of(CampaignEngineLayers.ABOVE)
    }

    override fun render(
        layer: CampaignEngineLayers?,
        viewport: ViewportAPI?
    ) {
        if (!isActiveOrInProgress) return
        if (layer != CampaignEngineLayers.ABOVE) return
        if (viewport == null) return
        val location = fleet.location
        if (!viewport.isNearViewport(location, 1000f)) return
        val arc = currArc + BUFFER
        val radius = getRadius()
        val opacityMult = progressFraction

        if (sprite == null) {
            sprite = Global.getSettings().getSprite("graphics/fx/shields256.png")
        }
        if (spriteTwo == null) {
            Global.getSettings().loadTexture("graphics/fx/shields256ringd.png")
            spriteTwo = Global.getSettings().getSprite("graphics/fx/shields256ringd.png")
        }

        sprite!!.bindTexture()
        GL11.glEnable(GL_TEXTURE_2D)
        GL11.glEnable(GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)
        GL11.glPushMatrix()
        GL11.glTranslatef(fleet.location.x, fleet.location.y, 0f)
        GL11.glRotatef(fleet.facing - (arc / 2f), 0f, 0f, 1f)

        for (i in 0..2) {
            var color = Color(125, 125, 255, 75)
            if (i == 2) {
                color = Color(255, 255, 255, 255)
                spriteTwo!!.bindTexture()
            }

            val scroll = texX
            val invertScroll = 1 - texX
            GL11.glBegin(GL11.GL_TRIANGLE_FAN)
            GL11.glColor4ub(color.red.toByte(), color.green.toByte(), color.blue.toByte(), 0)
            GL11.glTexCoord2f(0.5f, 0.5f)
            GL11.glVertex2f(0.0f, 0.0f)

            val var2 = 2f * radius * PI * arc / 360f
            var numPoints = ((var2 / 20f) + 1).coerceAtLeast(2.0).toInt() // ÓÔõo00
            val unknownTwo = ((arc / 5f) + 1).toInt()
            numPoints = numPoints.coerceAtLeast(unknownTwo)

            val unknownThree = numPoints - 1
            val radians = Math.toRadians((arc / unknownThree).toDouble())

            for (iter in 0..<numPoints) {
                var colorMult = opacityMult

                val degrees = Math.toDegrees(radians * iter.toDouble()).toFloat()
                val constant = 10f
                if (degrees < constant || arc - degrees < constant) {
                    colorMult = min(degrees, arc - degrees) / constant * opacityMult
                }

                GL11.glColor4ub(color.red.toByte(), color.green.toByte(), color.blue.toByte(), (colorMult * color.alpha).toInt().toByte())

                var pos = iter * radians

                if (i == 1) {
                    GL11.glTexCoord2f(
                        0.5f + cos((pos + invertScroll + (Math.PI.toFloat() / 4f))).toFloat() / 2.0f,
                        0.5f + sin((pos + invertScroll + (Math.PI.toFloat() / 4f))).toFloat() / 2.0f
                    )
                } else {
                    GL11.glTexCoord2f(
                        0.5f + cos((pos + scroll)).toFloat() / 2.0f,
                        0.5f + sin((pos + scroll)).toFloat() / 2.0f
                    )
                }

                val x = radius * cos(pos).toFloat()
                val y = radius * sin(pos).toFloat()

                if (false && !Global.getSector().isPaused) {
                    val color = if (iter == 1) Color.GREEN else if (iter == numPoints - 1) Color.BLUE else Color.RED
                    Global.getSector().currentLocation.addGlowyParticle(
                        Vector2f(fleet.location).translate(x, y),
                        Misc.ZERO,
                        5f,
                        0f,
                        0.05f,
                        color
                    )
                }

                GL11.glVertex2f(x, y)
            }

            GL11.glEnd()

        }
        GL11.glPopMatrix()
    }
}