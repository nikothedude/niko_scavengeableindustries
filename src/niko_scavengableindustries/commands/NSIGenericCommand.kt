package niko_scavengableindustries.commands

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignEngineLayers
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.util.Misc
import lunalib.lunaUtil.campaign.LunaCampaignRenderer
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin
import niko_scavengableindustries.NSIBaseNikoScript
import org.lazywizard.console.BaseCommand
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.util.vector.Vector2f
import org.magiclib.kotlin.addGlowyParticle
import java.awt.Color
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class NSIGenericCommand: BaseCommand {

    override fun runCommand(
        p0: String,
        p1: BaseCommand.CommandContext
    ): BaseCommand.CommandResult {

        return BaseCommand.CommandResult.SUCCESS
    }
}