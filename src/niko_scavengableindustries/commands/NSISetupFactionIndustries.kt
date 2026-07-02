package niko_scavengableindustries.commands

import niko_scavengableindustries.NSIModPlugin
import niko_scavengableindustries.NSISettings
import org.apache.log4j.Level
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandResult
import org.lazywizard.console.Console

class NSISetupFactionIndustries: BaseCommand {
    override fun runCommand(
        args: String,
        context: BaseCommand.CommandContext
    ): CommandResult {
        NSIModPlugin.setupFactionIndustryKnowledge()
        Console.showMessage("Industry knowledge has been reset.")

        return CommandResult.SUCCESS
    }
}