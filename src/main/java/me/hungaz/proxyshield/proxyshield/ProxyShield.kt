package me.hungaz.proxyshield.proxyshield

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.configuration.file.FileConfiguration
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class ProxyShield : JavaPlugin(), Listener {
    private lateinit var config: FileConfiguration
    private val proxyCache: MutableMap<String, CachedData> = HashMap()

    private var cacheExpirationTime: Long = 10

    private data class CachedData(val result: Int, val timestamp: Long)

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
        loadConfigSettings()
    }

    private fun loadConfigSettings() {
        cacheExpirationTime = config.getLong("cache_expiration", 15)

        cacheExpirationTime = TimeUnit.MINUTES.toMillis(cacheExpirationTime)
    }

    private fun getFromCache(playerIp: String): Int? {
        val cachedData = proxyCache[playerIp]
        return if (cachedData != null && System.currentTimeMillis() - cachedData.timestamp < cacheExpirationTime) {
            cachedData.result
        } else {
            null
        }
    }

    private fun saveToCache(playerIp: String, block: Int) {
        proxyCache[playerIp] = CachedData(block, System.currentTimeMillis())
    }

    private fun performProxyCheck(playerIp: String): Int {
        val cachedResult = getFromCache(playerIp)
        if (cachedResult != null) {
            return cachedResult
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
                    return -1
                }
                422 -> {
                    logger.severe("The IPHub API cannot process the request. Check your API key and request URL.")
                    return -1
                }
                429 -> {
                    logger.severe("You've exceeded your API Key available queries.")
                    return -1
                }
                502 -> {
                    logger.severe("Looks like the IPHub servers are having trouble, IPs will be ignored.")
                    return -1
                }
                503 -> {
                    logger.severe("IPHub servers are overloaded or down; IPs will be ignored.")
                    return -1
                }
            }

            val response = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }
            val json = JSONObject(response)
            val block = json.getInt("block")

            proxyCache[playerIp] = CachedData(block, System.currentTimeMillis())
            saveToCache(playerIp, block)
            return block
        } catch (e: IOException) {
            logger.warning("Failed to perform the Proxy check for IP: $playerIp")
            e.printStackTrace()
        }
        return -1
    }

    @EventHandler
    fun onAsyncPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        val playerName = event.name
        val playerIp = event.address.hostAddress

        if (playerIp.startsWith("127.0.") || playerIp.startsWith("192.168.") || playerIp.startsWith("10.") || playerIp.startsWith("OpenWrt") || playerIp == "0:0:0:0:0:0:0:1") {
            logger.info("$playerName is trying to join with a local IP, ignoring their IP.")
            return
        }

        val block = performProxyCheck(playerIp)
        if (block == -1) {
            logger.warning("Invalid IP address for player $playerName: $playerIp")
            return
        }

        if (block == 1) {
            val kickMessage = config.getString("kick_message") ?: "VPN/Proxies aren't allowed on this server. Please consider disabling it."
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage)
            logger.info("$playerName's IP has been flagged as a Proxy")
        } else {
            val showPassedMessage = config.getBoolean("show_passed_check_message", true)
            if (showPassedMessage) {
                logger.info("$playerName has passed the VPN/Proxy check.")
            }
        }
    }
}
