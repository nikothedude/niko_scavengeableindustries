package niko_scavengableindustries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.FactionAPI
import lunalib.lunaSettings.LunaSettings
import niko_scavengableindustries.utils.DebugUtils
import org.lazywizard.lazylib.ext.json.iterator
import kotlin.collections.set

object NSISettings {

    var indEvoEnabled = false
    var patchLibEnabled = false

    var lockIndEvo = false
    var lockVanilla = false

    val industrySpecs = HashMap<String, IndustryGenSpec>()
    val TEMP_FLAGS = HashSet<String>()

    const val DROP_MULT_PER_DROPPABLE = 0.1f

    val factionsToTags = genFactionsToTags()

    fun genFactionsToTags(): HashMap<String, MutableSet<String>> {
        val map = HashMap<String, MutableSet<String>>()

        for (obj in Global.getSettings().getMergedSpreadsheetData("faction", "data/world/factions/factions.csv")) {
            val tags = HashSet<String>()
            val sourcePath = obj.getString("faction") ?: continue // gets the path where all instances of this faction should be

            DebugUtils.log.info("loading $sourcePath")
            val mergedJson = Global.getSettings().getMergedJSON(sourcePath)
            var id: String? = null
            if (mergedJson.has("id")) {
                id = mergedJson.getString("id")
            }
            if (id != null && mergedJson.has("knownHullMods")) {
                val hmodObj = mergedJson.getJSONObject("knownHullMods")
                if (hmodObj.has("tags")) {
                    val tagsArray = hmodObj.getJSONArray("tags")
                    for (i in 0 until tagsArray.length()) {
                        val tag = tagsArray.get(i).toString()
                        tags += tag
                    }
                }
            }
            if (id != null) {
                map[id] = tags
            }
        }
        return map
    }

    fun FactionAPI.getHullmodTags(): MutableSet<String> {
        if (factionsToTags[id] == null) return HashSet()
        return factionsToTags[id]!!
    }

    fun loadSettings() {
        getEnabledMods()

        lockIndEvo = LunaSettings.getBoolean(Ids.MOD_ID, "NSI_LockIndEvoStructures")!!
        lockVanilla = LunaSettings.getBoolean(Ids.MOD_ID, "NSI_LockVanillaStructures")!!

        lockModStructures()
        loadGenDataFromCSV()
    }

    private fun lockModStructures() {
        if (patchLibEnabled && indEvoEnabled && lockIndEvo) {
            TEMP_FLAGS += "LOCK_INDEVO"
        }
        if (lockVanilla) {
            TEMP_FLAGS += "LOCK_VANILLA"
        }
    }

    fun getEnabledMods() {
        val manager = Global.getSettings().modManager ?: return
        indEvoEnabled = manager.isModEnabled("IndEvo")
        patchLibEnabled = manager.isModEnabled("patchlib")
    }

    fun loadGenDataFromCSV() {
        industrySpecs.clear()

        val csv = Global.getSettings().getMergedSpreadsheetDataForMod("id", Ids.GEN_DATA_PATH, Ids.MOD_ID)

        for (index in 0 until csv.length())
        {
            val row = csv.getJSONObject(index)

            val id = row.getString("id")
            if (id.startsWith("#") || id.isEmpty()) continue

            var reqModIds = HashSet<String>()
            val reqModData = row.optString("req_mod_ids")
            if (reqModData.isNotEmpty()) {
                reqModIds = reqModData.split(Regex("(, *)")).toHashSet()
                var skip = false
                for (modId in reqModIds) {
                    if (!Global.getSettings().modManager.isModEnabled(modId)) {
                        DebugUtils.log.info("industry $id missing mod $modId, skipping")
                        skip = true
                        break
                    }
                }
                if (skip) {
                    continue
                }
            }
            val dropWeight = row.getDouble("drop_weight").toFloat()
            val sellWeight = row.getDouble("sell_weight").toFloat()
            val knownByData = row.optString("known_by")
            var knownBy = HashSet<String>()
            if (knownByData.isNotEmpty()) {
                knownBy = knownByData.split(Regex("(, *)")).toHashSet()
            }
            var reqFlags = HashSet<String>()
            val reqFlagData = row.optString("req_flags")
            if (reqFlagData.isNotEmpty()) {
                reqFlags = reqFlagData.split(Regex("(, *)")).toHashSet()

                var skip = false
                for (flag in reqFlags) {
                    if (!TEMP_FLAGS.contains(flag)) {
                        skip = true
                        break
                    }
                }
                if (skip) {
                    continue
                }
            }

            val discoveryString = row.optString("discovery_text") ?: ""
            val upgradeTo = row.optString("upgrade_to") ?: ""

            val spec = IndustryGenSpec(
                id,
                dropWeight,
                sellWeight,
                reqModIds,
                reqFlags,
                knownBy,
                discoveryString,
                upgradeTo
            )
            industrySpecs[id] = spec
        }

        TEMP_FLAGS.clear()
    }

    fun getDropChanceMult(): Float {
        val numDroppable = industrySpecs.map { it.value.weight > 0 }.size
        val mult = numDroppable * DROP_MULT_PER_DROPPABLE
        return mult
    }
}