package niko_scavengableindustries

import com.fs.starfarer.api.Global
import lunalib.lunaSettings.LunaSettings
import niko_scavengableindustries.utils.DebugUtils
import kotlin.collections.set

object NSISettings {

    var indEvoEnabled = false
    var lockIndEvo = false

    val industrySpecs = HashMap<String, IndustryGenSpec>()
    val TEMP_FLAGS = HashSet<String>()

    const val DROP_MULT_PER_DROPPABLE = 0.05f

    fun loadSettings() {
        getEnabledMods()

        lockIndEvo = LunaSettings.getBoolean(Ids.MOD_ID, "NSI_LockIndEvoStructures")!!

        lockModStructures()
        loadGenDataFromCSV()
    }

    private fun lockModStructures() {
        if (indEvoEnabled && lockIndEvo) {
            TEMP_FLAGS += "LOCK_INDEVO"
        }
    }

    fun getEnabledMods() {
        val manager = Global.getSettings().modManager ?: return
        indEvoEnabled = manager.isModEnabled("IndEvo")
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

                for (flag in reqFlags) {
                    if (!TEMP_FLAGS.contains(flag)) {
                        continue
                    }
                }
            }

            val discoveryString = row.optString("discovery_text") ?: ""

            val spec = IndustryGenSpec(
                id,
                dropWeight,
                sellWeight,
                reqModIds,
                reqFlags,
                knownBy,
                discoveryString
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