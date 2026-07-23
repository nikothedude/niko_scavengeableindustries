package niko_scavengableindustries.patches

import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI
import niko_scavengableindustries.IndustryBPItem
import patchlib.api.context.AfterContext
import patchlib.api.match.ClassMatch
import patchlib.api.match.MethodMatch
import patchlib.api.patch.After
import patchlib.api.patch.Patch

@Patch(target = ClassMatch(subtype = CargoTransferHandlerAPI::class))
object StackPricePatch {

    @JvmStatic
    @After(target = MethodMatch(methodName = "computeCurrentSingleItemBuyCost"))
    fun modifyBuy(context: AfterContext) {
        val stack: CargoStackAPI = context.getArg(0) as? CargoStackAPI ?: return
        if (!stack.isSpecialStack) return
        val plugin = stack.plugin ?: return
        if (plugin !is IndustryBPItem) return

        plugin.beingBought = true
        context.returnValue = (plugin.getPrice(null, null)).toFloat()
        plugin.beingBought = false
    }

    @JvmStatic
    @After(target = MethodMatch(methodName = "computeBuyCostOfItem"))
    fun modifyBuyStack(context: AfterContext) {
        val stack: CargoStackAPI = context.getArg(0) as? CargoStackAPI ?: return
        if (!stack.isSpecialStack) return
        val plugin = stack.plugin ?: return
        if (plugin !is IndustryBPItem) return

        plugin.beingBought = true
        context.returnValue = (plugin.getPrice(null, null) * stack.size)
        plugin.beingBought = false
    }
}