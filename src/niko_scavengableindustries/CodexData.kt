package niko_scavengableindustries

import com.fs.starfarer.api.impl.campaign.ids.Industries
import com.fs.starfarer.api.impl.codex.CodexDataV2
import com.fs.starfarer.api.loading.IndustrySpecAPI

object CodexData {
    fun linkCodexEntries() {
        createReciprocalLink(CodexDataV2.getIndustryEntryId("NSI_SpyBureau"), CodexDataV2.getIndustryEntryId("NSI_SpyOutpost"))

        val indCat = CodexDataV2.getEntry(CodexDataV2.CAT_INDUSTRIES)
        for (indEntry in indCat.children) {
            val spec = indEntry.param as? IndustrySpecAPI ?: continue

            if (spec.hasTag(Industries.TAG_GROUNDDEFENSES)) {
                createReciprocalLink(CodexDataV2.getIndustryEntryId("NSI_ASBUplink"), indEntry.id)
            }
            if (spec.hasTag(Industries.TAG_MILITARY)) {
                createReciprocalLink(CodexDataV2.getIndustryEntryId("NSI_trainingGrounds"), indEntry.id)
            }
            if (spec.hasTag(Industries.TAG_HEAVYINDUSTRY)) {
                createReciprocalLink(CodexDataV2.getIndustryEntryId("NSI_expertDockworks"), indEntry.id)
            }
            if (spec.hasTag(Industries.MINING)) {
                createReciprocalLink(CodexDataV2.getIndustryEntryId("NSI_blastCoreMining"), indEntry.id)
            }
        }
        createReciprocalLink(CodexDataV2.getIndustryEntryId("NSI_SpyBureau"), CodexDataV2.getIndustryEntryId("NSI_SpyOutpost"))
    }

    private fun createReciprocalLink(entryIdOne: String, entryIdTwo: String) {
        val entryOne = CodexDataV2.getEntry(entryIdOne)
        val entryTwo = CodexDataV2.getEntry(entryIdTwo)

        entryOne.addRelatedEntry(entryTwo)
        entryTwo.addRelatedEntry(entryOne)
    }
}