package me.hungaz.proxyshield.proxyshield

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.configuration.file.FileConfiguration
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
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

    private fun getPublicIPAddress(): String? {
        try {
            val url = URL("https://api.ipify.org?format=json")
            val connection = url.openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()

            val reader = connection.getInputStream().bufferedReader()
            val response = reader.readLine().trim()
            reader.close()

            val json = JSONObject(response)
            return json.getString("ip")
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    @EventHandler
    fun onAsyncPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        val playerName = event.name
        val ipAddress = getPublicIPAddress()

        if (ipAddress == null) {
            logger.warning("Failed to get the public IP address of $playerName.")
            return
        }

        val inetAddress = try {
            InetAddress.getByName(ipAddress)
        } catch (e: Exception) {
            logger.warning("Invalid IP address for player $playerName: $ipAddress")
            return
        }

        val playerIp = inetAddress.hostAddress

        if (playerIp.startsWith("127.0.") || playerIp.startsWith("192.168.") || playerIp.startsWith("10.") || playerIp == "0:0:0:0:0:0:0:1") {
            logger.info("$playerName is trying to join with a local IP, ignoring their IP.")
            return
        }

        val apiKey = config.getString("api_key", "YOUR_API_KEY")
        val requestUrl = "http://v2.api.iphub.info/ip/$playerIp?key=$apiKey"

        try {
            val url = URL(requestUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()

            when (connection.responseCode) {
                403 -> {
                    logger.severe("Invalid API Key, please take a look at your config file sir.")
                    return
                }
                422 -> {
                    logger.severe("The IPHub API cannot process the request. Check your API key and request URL.")
                    return
                }
                429 -> {
                    logger.severe("You've exceeded your API Key available queries.")
                    return
                }
                502 -> {
                    logger.severe("Looks like the IPHub servers are having trouble, IPs will be ignored.")
                    return
                }
                503 -> {
                    logger.severe("IPHub servers are overloaded or down; IPs will be ignored.")
                    return
                }
            }

            val response = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }
            val json = JSONObject(response)
            val block = json.getInt("block")
            if (block == 1) {
                val kickMessage = config.getString("kick_message") ?: "VPN/Proxies aren't allowed on this server. Please consider disabling it."
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage)
                logger.info("$playerName's IP has been flagged as a Proxy")
                return
            } else {
                val showPassedMessage = config.getBoolean("show_passed_check_message", true)
                if (showPassedMessage) {
                    logger.info("$playerName has passed the VPN/Proxy check.")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
