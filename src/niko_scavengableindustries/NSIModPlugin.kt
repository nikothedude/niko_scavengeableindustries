package niko_scavengableindustries

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.listeners.ColonyCrisesSetupListener
import com.fs.starfarer.api.impl.campaign.ids.Items
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel
import lunalib.lunaSettings.LunaSettings
import lunalib.lunaSettings.LunaSettingsListener
import niko_scavengableindustries.industries.SpyBureau.SpyBureauDefenseFactor

class NSIModPlugin : BaseModPlugin() {

    companion object {
        fun setupFactionIndustryKnowledge() {
            for (fac in Global.getSector().allFactions) {
                for (spec in NSISettings.industrySpecs.values) {
                    if (spec.knownBy.contains("base_bp")) {
                        fac.addKnownIndustry(spec.id)
                        break
                    }

                    if (spec.knownBy.contains(fac.id)) {
                        fac.addKnownIndustry(spec.id)
                    } else {
                        fac.removeKnownIndustry(spec.id)
                    }
                }
            }
        }
    }

    /*This method is run right at the end of starsectors loading.
       * It is most useful for loading data that only really needs to be setup once. */
    @Throws(Exception::class)
    override fun onApplicationLoad() {
        Global.getSettings().getSpecialItemSpec(Items.PLASMA_DYNAMO).params += ", NSI_nebulaSiphoner"

        for (spec in Global.getSettings().allSpecialItemSpecs) {
            if (spec.tags.contains("nanoforge")) {
                spec.params += ", NSI_expertDockworks"
            }
        }

        LunaSettings.addSettingsListener(NSISettingsChangedListener())

        return
    }

    class NSISettingsChangedListener: LunaSettingsListener {
        override fun settingsChanged(modID: String) {
            NSISettings.loadSettings()
        }
    }

    /*This method is run in two cases:
    * - At the end of the creation of a new save
    * - When an existing save finished loading
    * This method is most useful for adding transient listeners/scripts and for enabling mid-save compatibility,
    * like adding star systems to an existing save if the mod was just added. */
    override fun onGameLoad(newGame: Boolean) {
        NSISettings.loadSettings()

        Global.getSector().addTransientListener(NSIBPShopAdder())
        for (listener in Global.getSector().listenerManager.getListeners(NSILootListener::class.java).toSet()) {
            Global.getSector().listenerManager.removeListener(listener)
            // TODO remove later
        }
        Global.getSector().listenerManager.addListener(NSILootListener(), true)
        Global.getSector().listenerManager.addListener(NSIHAFactorAdder(), true)
    }

    class NSIHAFactorAdder: ColonyCrisesSetupListener {
        override fun finishedAddingCrisisFactors(intel: HostileActivityEventIntel?) {
            if (intel == null) return

            intel.addFactor(SpyBureauDefenseFactor())
        }
    }

    /*Runs when a save is created.
    * This method specifically runs before procedural generation, so any base-game procedural content is not accessible yet.
    * It is recommended to start placing your modded star systems from here,
    * as starsectors procgen will avoid placing stars and hyperspace storms nearby existing systems, preventing overlap.*/
    override fun onNewGame() {
    }

    /*Runs after onNewGame, after the economy has finished loading.
    * This method can be useful for accessing other mods star systems, assuming those have placed their systems in onNewGame. */
    override fun onNewGameAfterEconomyLoad() {
    }

    override fun onNewGameAfterProcGen() {
    }

    override fun onNewGameAfterTimePass() {
        setupFactionIndustryKnowledge()
    }
}
