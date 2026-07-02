package niko_scavengableindustries.commands

import niko_scavengableindustries.NSISettings
import org.apache.log4j.Level
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandResult
import org.lazywizard.console.Console

class NSILoadSettings: BaseCommand {
    override fun runCommand(
        args: String,
        context: BaseCommand.CommandContext
    ): CommandResult {
        try {
            NSISettings.loadSettings()
        } catch (ex: Exception) {
            val errorCode = "runCommand failed due to thrown exception: $ex, ${ex.cause}"
            Console.showMessage(errorCode, Level.ERROR)
            return CommandResult.ERROR
        }
        Console.showMessage("Success! Settings have been reloaded.")

        return CommandResult.SUCCESS
    }
}