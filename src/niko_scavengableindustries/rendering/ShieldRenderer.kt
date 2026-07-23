package niko_scavengableindustries.rendering

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignEngineLayers
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.util.Misc
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

object ShieldRenderer {

    /**
     * @param sprite the sprite to be rendered
     * @param loc the location to render the sprite at
     * @param radius the radius of the circle
     * @param numPoints the number of points
     * @param texX where the x coord of the center of the circle is placed, within the sprite. between 0 and 1, gets scaled to the actual width of the sprite
     * @param texY where the y coord of the center of the circle is placed, within of the sprite. between 0 and 1, gets scaled to the actual height of the sprite
     * @param texRadius the radius of the circle in terms of the sprite. between 0 and 1, does not get scaled in terms of the sprite
     */
    //does the actual maths. if you want, you can rip out the drawPoints call from here and replace it with a series of glVertex2f, glTexCoord2f & glColor4b calls
    fun renderCircularCutoutFromSprite(
        sprite: SpriteAPI,
        loc: Vector2f,
        radius: Float,
        startAngle: Float,
        arc: Float,
        numPoints: Int,
        texX: Float,
        texY: Float,
        texRadius: Float,
        color: Color,
        alpha: Int
    ) {
        //make arrays for the points

        var texX = texX
        var texY = texY
        val vertices = FloatArray((numPoints + 2) * 2)
        val texCoords = FloatArray((numPoints + 2) * 2)

        //scale by texture size. should always be square
        texX *= sprite.textureWidth
        texY *= sprite.textureHeight

        //quick maffs
        val angleDiffPerPoint = arc / numPoints

        //set the first vertex to be at the center of the ship
        vertices[0] = loc.x
        vertices[1] = loc.y

        //ditto for the texture coordsm but at the center of the texture
        texCoords[0] = texX
        texCoords[1] = texY

        val texCenter = Vector2f(texX, texY) //reduce allocations yay

        //for each point we want in the outside of the arc
        for (i in 0..<numPoints + 1) {
            //get the points

            val point = MathUtils.getPointOnCircumference(loc, radius, startAngle + (angleDiffPerPoint * i))
            val texCoord =
                MathUtils.getPointOnCircumference(texCenter, texRadius, startAngle + (angleDiffPerPoint * i))

            //some vague fuckery because the arrays are alternating x,y,x,y,x,y etc etc
            val indexReal = i * 2 + 2
            // orig shield rendering uses vertex2f with radius then * cos and * sin for x y...

            val a = 1f
            val b = 1f
            val c = 0f
            val y = a * sin(b * 1f) * (-1f).pow(c)

            vertices[indexReal] = point.x
            vertices[indexReal + 1] = point.y

            texCoords[indexReal] = texCoord.x * y
            texCoords[indexReal + 1] = texCoord.y * y
        }

        //tell the game to use this texture while drawing
        sprite.bindTexture()
        //draw the thing
        drawPoints(
            vertices,
            texCoords,
            Color(color.red, color.green, color.blue, alpha),
            1f,
            GL11.GL_TRIANGLE_FAN,
            GL11.GL_SRC_ALPHA,
            GL11.GL_ONE_MINUS_SRC_ALPHA
        )
    }

    /**
     * @param vertices array of vertices, elements n & n + 1 make up vertex n
     * @param texCoords array of tex coords, elements n & n + 1 are applied to vertex n
     * @param color colour for the entire shape
     * @param alphaMult an additional alpha mult
     * @param mode the openGL mode to use to render the shape
     */
    //taken from my personal helper collection. I can't explain this well atm. isn't the per-vertex colour version because I'd be compelled to do some optimisations that I cba to rn if I touched that
    fun drawPoints(
        vertices: FloatArray,
        texCoords: FloatArray,
        color: Color,
        alphaMult: Float,
        mode: Int,
        blendSrc: Int,
        blendDest: Int
    ) {
        val vertexBuffer = BufferUtils.createFloatBuffer(vertices.size)
        val texCoordBuffer = BufferUtils.createFloatBuffer(texCoords.size)

        vertexBuffer.put(vertices)
        vertexBuffer.flip()
        texCoordBuffer.put(texCoords)
        texCoordBuffer.flip()

        Misc.setColor(color, alphaMult)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(blendSrc, blendDest)
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT)
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY)
        GL11.glVertexPointer(2, 0, vertexBuffer)
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        GL11.glTexCoordPointer(2, 0, texCoordBuffer)
        GL11.glDrawArrays(mode, 0, vertices.size / 2)
        GL11.glPopClientAttrib()
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_BLEND)
    }

}
