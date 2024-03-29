package me.hungaz.proxyshield.proxyshield

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class Commands(private val plugin: JavaPlugin) : CommandExecutor {
    private lateinit var config: FileConfiguration
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
                sender.sendMessage("§cNo commands.")
                return true
             }
        if (args[0].equals("reload")) {
            if(sender is Player && !sender.hasPermission("proxyguard.admin")) {
                sender.sendMessage("§cYou don't have permission to execute this command.")
                return true
            }
            plugin.reloadConfig()
            config = plugin.config
            plugin.onDisable()
            plugin.onEnable()
            sender.sendMessage("§aProxyShield config reloaded.")
            return true
        }
        return false
    }
}
