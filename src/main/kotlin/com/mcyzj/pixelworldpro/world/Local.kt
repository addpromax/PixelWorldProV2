package com.mcyzj.pixelworldpro.world

import com.mcyzj.pixelworldpro.PixelWorldPro
import com.mcyzj.pixelworldpro.api.interfaces.WorldAPI
import com.mcyzj.pixelworldpro.compress.Zip
import com.mcyzj.pixelworldpro.config.Config
import com.mcyzj.pixelworldpro.server.World.localWorld
import com.xbaimiao.easylib.bridge.economy.PlayerPoints
import com.xbaimiao.easylib.bridge.economy.Vault
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture

object Local {
    private val logger = PixelWorldPro.instance.logger
    private var config = Config.config
    private val lang = PixelWorldPro.instance.lang
    private val database = PixelWorldPro.databaseApi
    private var file = Config.file
    private var worldConfig = Config.world
    fun adminCreateWorld(owner: UUID, template: String?): CompletableFuture<Boolean>{
        val future = CompletableFuture<Boolean>()
        val temp = if (template == null){
            val templatePath = file.getString("Template.Path")
            if (templatePath == null){
                logger.warning("§aPixelWorldPro ${lang.getString("worldConfig.warning.template.pathNotSet")}")
                future.complete(false)
                return future
            } else {
                val templateFile = File(templatePath)
                val templateList = templateFile.list()!!
                templateList[Random().nextInt(templateList.size)]
            }
        }else{
            template
        }
        val worldApi = WorldAPI.Factory.get()
        return worldApi.createWorld(owner, temp)
    }
    fun adminLoadWorld(owner: UUID): CompletableFuture<Boolean>{
        val worldApi = WorldAPI.Factory.get()
        return worldApi.loadWorld(owner)
    }
    fun adminLoadWorld(id: Int): CompletableFuture<Boolean>{
        val worldApi = WorldAPI.Factory.get()
        return worldApi.loadWorld(id)
    }
    fun adminUnloadWorld(owner: UUID): CompletableFuture<Boolean>{
        val worldApi = WorldAPI.Factory.get()
        return worldApi.unloadWorld(owner)
    }
    fun adminUnloadWorld(id: Int): CompletableFuture<Boolean>{
        val worldApi = WorldAPI.Factory.get()
        return worldApi.unloadWorld(id)
    }

    fun unloadAllWorld(){
        for (key in localWorld.keys){
            logger.info("§aPixelWorldPro 使用线程：${Thread.currentThread().name} 进行世界卸载操作")
            //拉取世界数据
            val worldData = database.getWorldData(key)
            if (worldData == null){
                logger.warning("§aPixelWorldPro $key ${lang.getString("worldConfig.warning.unload.noWorldData")}")
                return
            }
            //获取世界
            val world = localWorld[worldData.id]
            if (world == null) {
                localWorld.remove(worldData.id)
                return
            }
            if (Bukkit.unloadWorld(world, true)){
                localWorld.remove(worldData.id)
                Zip.toZip(worldData.world, worldData.world)
                File(file.getString("World.Server"), worldData.world).deleteRecursively()
            }
        }
    }

    fun createWorld(owner: UUID, template: String?){
        if (database.getWorldData(owner) != null){
            Bukkit.getPlayer(owner)?.sendMessage(lang.getString("worldConfig.warning.create.created")?:"无法创建世界：对象已经有一个世界了")
            return
        }
        val player = Bukkit.getPlayer(owner)!!
        if (!checkCreateMoney(owner)){
            player.sendMessage(lang.getString("worldConfig.warning.create.notEnough")?: "无法创建世界：所需的资源不足")
        }
        val temp = if (template == null){
            val templatePath = file.getString("Template.Path")
            if (templatePath == null){
                logger.warning("§aPixelWorldPro ${lang.getString("worldConfig.warning.template.pathNotSet")}")
                return
            } else {
                val templateFile = File(templatePath)
                val templateList = templateFile.list()!!
                templateList[Random().nextInt(templateList.size)]
            }
        }else{
            template
        }
        val worldApi = WorldAPI.Factory.get()
        val future = worldApi.createWorld(owner, temp)
        future.thenApply {
            if (!takeCreateMoney(owner)){
                player.sendMessage(lang.getString("worldConfig.warning.create.notEnough")?: "无法创建世界：所需的资源不足")
            }
        }
    }

    private fun checkCreateMoney(user: UUID):Boolean{
        val player = Bukkit.getPlayer(user) ?: return false
        val useList = worldConfig.getConfigurationSection("Create.Use")!!.getKeys(false)
        useList.remove("Default")
        if (useList.isNotEmpty()){
            for (use in useList){
                val permission = worldConfig.getString("Create.Use.$use.Permission")!!
                if (!player.hasPermission(permission)){
                    return false
                }
                if (worldConfig.getDouble("Create.Use.$use.Money") > 0.0) {
                    if (!Vault().has(player, worldConfig.getDouble("Create.Use.$use.Money"))) {
                        return false
                    }
                }
                if (worldConfig.getDouble("Create.Use.$use.Point") > 0.0) {
                    if (!PlayerPoints().has(player, worldConfig.getDouble("Create.Use.$use.Point"))) {
                        return false
                    }
                }
                return true
            }
        }
        val permission = worldConfig.getString("Create.Use.Default.Permission")!!
        if (!player.hasPermission(permission)){
            return false
        }
        if (worldConfig.getDouble("Create.Use.Default.Money") > 0.0) {
            if (!Vault().has(player, worldConfig.getDouble("Create.Use.Default.Money"))) {
                return false
            }
        }
        if (worldConfig.getDouble("Create.Use.Default.Point") > 0.0) {
            if (!PlayerPoints().has(player, worldConfig.getDouble("Create.Use.Default.Point"))) {
                return false
            }
        }
        return true
    }

    private fun takeCreateMoney(user: UUID):Boolean{
        val player = Bukkit.getPlayer(user) ?: return false
        val useList = worldConfig.getConfigurationSection("Create.Use")!!.getKeys(false)
        useList.remove("Default")
        if (useList.isNotEmpty()){
            for (use in useList){
                val permission = worldConfig.getString("Create.Use.$use.Permission")!!
                if (!player.hasPermission(permission)){
                    return false
                }
                if (worldConfig.getDouble("Create.Use.$use.Money") > 0.0) {
                    if (!Vault().has(player, worldConfig.getDouble("Create.Use.$use.Money"))) {
                        return false
                    }
                    Vault().take(player, worldConfig.getDouble("Create.Use.$use.Money"))
                }
                if (worldConfig.getDouble("Create.Use.$use.Point") > 0.0) {
                    if (!PlayerPoints().has(player, worldConfig.getDouble("Create.Use.$use.Point"))) {
                        return false
                    }
                    PlayerPoints().take(player, worldConfig.getDouble("Create.Use.$use.Point"))
                }
                return true
            }
        }
        val permission = worldConfig.getString("Create.Use.Default.Permission")!!
        if (!player.hasPermission(permission)){
            return false
        }
        if (worldConfig.getDouble("Create.Use.Default.Money") > 0.0) {
            if (!Vault().has(player, worldConfig.getDouble("Create.Use.Default.Money"))) {
                return false
            }
            Vault().take(player, worldConfig.getDouble("Create.Use.Default.Money"))
        }
        if (worldConfig.getDouble("Create.Use.Default.Point") > 0.0) {
            if (!PlayerPoints().has(player, worldConfig.getDouble("Create.Use.Default.Point"))) {
                return false
            }
            PlayerPoints().take(player, worldConfig.getDouble("Create.Use.Default.Point"))
        }
        return true
    }

    fun adminTpWorldId(player: Player, id: Int){
        val world = localWorld[id]
        if (world == null){
            player.sendMessage(lang.getString("worldConfig.warning.tp.notLoad")?:"无法传送至世界：世界未加载")
            return
        }
        val location = world.spawnLocation
        player.teleport(location)
    }

    fun tpWorldId(player: Player, id: Int){
        var world = localWorld[id]
        if (world == null){
            adminLoadWorld(id).thenApply {
                tpWorldId(player, id)
            }
        }
        world = localWorld[id]?:return
        val location = world.spawnLocation
        player.teleport(location)
    }

    fun getWorldNameUUID(worldName: String): UUID? {
        val realNamelist = worldName.split("/").size
        if (realNamelist < 2) {
            return null
        }
        val realName = worldName.split("/")[realNamelist - 2]
        val uuidString: String? = Regex(pattern = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-z]{12}")
            .find(realName)?.value
        return try{
            UUID.fromString(uuidString)
        }catch (_:Exception){
            null
        }
    }
}