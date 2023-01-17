package me.hungaz.proxyshield.proxyshield

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.configuration.file.FileConfiguration

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

import org.json.JSONObject
import java.io.File

class ProxyShield : JavaPlugin(), Listener {
    private lateinit var config: FileConfiguration

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)

        val cmd = getCommand("proxyshield")
        cmd?.setExecutor(Commands(this))

        if (!dataFolder.exists()) {
            dataFolder.mkdir()
        }
        val configFile = File(dataFolder, "config.yml")
        if (!configFile.exists()) {
            saveDefaultConfig()
        }

        config = getConfig()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val apiKey = config.getString("api_key")
        val player = event.player
        val playerIp = event.player.address?.hostString

        if (playerIp?.startsWith("127.0.") == true || playerIp?.startsWith("192.168.") == true || playerIp?.startsWith("10.") == true || playerIp == "localhost" || playerIp == "0.0.0.0") {
            logger.info("Player ${player.name} is using local IP, ignore their IP.")
            return
        }
        val requestUrl = "http://v2.api.iphub.info/ip/$playerIp?key=$apiKey"

        try {
            val url = URL(requestUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            if(connection.responseCode == 403){
                logger.severe("Invalid API Key, please check your config file.")
                return
            }
            if(connection.responseCode == 429){
                logger.severe("You've exceeded your API Key available queries.")
                return
            }
            if(connection.responseCode == 502){
                logger.severe("Looks like that the IPHub servers are having trouble, IPs will be ignored.")
                return
            }
            if(connection.responseCode == 503){
                logger.severe("IPHub servers are overloaded/downed, IPs will be ignored.")
                return
            }

            val response = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }
            val json = JSONObject(response)
            val block = json.getInt("block")
            if (block == 1) {
                val kickMessage = config.getString("kick_message")
                event.player.kickPlayer(kickMessage)
                logger.info("Player ${player.name} IP has been flagged as a Proxy")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}