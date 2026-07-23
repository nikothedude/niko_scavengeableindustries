package niko_scavengableindustries

data class IndustryGenSpec(
    val id: String,
    val weight: Float,
    val sellWeight: Float,
    val reqModIds: HashSet<String>,
    val reqFlags: HashSet<String>,
    val knownBy: HashSet<String>,
    val discoveryString: String,
    val upgradeTo: String
) {
}