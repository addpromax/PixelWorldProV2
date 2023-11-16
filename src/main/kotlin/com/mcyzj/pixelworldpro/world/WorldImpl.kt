package com.mcyzj.pixelworldpro.world

import com.mcyzj.pixelworldpro.PixelWorldPro
import com.mcyzj.pixelworldpro.api.interfaces.core.permission.PermissionAPI
import com.mcyzj.pixelworldpro.api.interfaces.core.world.WorldAPI
import com.mcyzj.pixelworldpro.compress.Zip
import com.mcyzj.pixelworldpro.file.Config
import com.mcyzj.pixelworldpro.data.dataclass.WorldCreateData
import com.mcyzj.pixelworldpro.expansion.listener.trigger.world.WorldSuccess
import com.mcyzj.pixelworldpro.server.World
import com.mcyzj.pixelworldpro.server.World.localWorld
import com.xbaimiao.easylib.module.utils.submit
import org.bukkit.Bukkit
import org.bukkit.WorldCreator
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.lang.Thread.sleep
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CompletableFuture


object WorldImpl : WorldAPI {
    private val logger = PixelWorldPro.instance.logger
    private val lang = PixelWorldPro.instance.lang
    private var config = Config.config
    private val database = PixelWorldPro.databaseApi
    private var fileConfig = Config.file
    private var worldConfig = Config.world
    private var bungee = Config.bungee

    private val asyncLoad = config.getBoolean("async.world.load")

    override fun createWorld(owner: UUID, template: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        submit(async = asyncLoad) {
            logger.info("§aPixelWorldPro 使用线程：${Thread.currentThread().name} 进行世界创建操作")
            //检查模板文件
            if (!World.checkTemplate(template)) {
                future.complete(false)
                return@submit
            }
            //获取time时间
            val time = System.currentTimeMillis()
            val date = Date(time)
            //把time时间格式化
            val formatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
            //把time时间格式化为字符串
            val timeString = formatter.format(date)
            //获取路径下对应的world文件夹
            val worldName = "${owner}_$timeString"
            //复制模板文件
            File(fileConfig.getString("Template.Path"), template).copyRecursively(
                File(
                    fileConfig.getString("World.Server"),
                    worldName
                )
            )
            //加载世界
            val worldCreator = WorldCreator("PixelWorldPro/$worldName/world")
            val world = Bukkit.createWorld(worldCreator)
            if (world == null) {
                future.complete(false)
                return@submit
            }
            val worldCreateData = WorldCreateData(
                owner,
                owner.toString(),
                worldName,
                PermissionAPI.Factory.get().getConfigWorldPermission(),
                HashMap<UUID, String>(),
                HashMap<String, Boolean>()
            )
            val worldData = database.createWorldData(worldCreateData)
            localWorld[worldData.id] = world
            World.setLock(worldData.id)
            WorldSuccess.createWorldSuccess(worldData, template)
            future.complete(true)
        }
        return future
    }

    override fun loadWorld(id: Int): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        submit(async = asyncLoad) {
            logger.info("§aPixelWorldPro 使用线程：${Thread.currentThread().name} 进行世界加载操作")
            //拉取世界数据
            val worldData = database.getWorldData(id)
            if (worldData == null){
                logger.warning("§aPixelWorldPro $id ${lang.getString("world.warning.load.noWorldData")}")
                future.complete(false)
                return@submit
            }
            //解压世界数据
            Zip.unzip(worldData.world, worldData.world)
            //加载世界
            val worldCreator = WorldCreator("PixelWorldPro/${worldData.world}/world")
            val world = Bukkit.createWorld(worldCreator)
            if (world == null) {
                future.complete(false)
                return@submit
            }
            localWorld[worldData.id] = world
            World.setLock(worldData.id)
            world.keepSpawnInMemory = false
            WorldSuccess.loadWorldSuccess(worldData)
            future.complete(true)
        }
        return future
    }

    override fun loadWorld(owner: UUID): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        submit(async = asyncLoad) {
            logger.info("§aPixelWorldPro 使用线程：${Thread.currentThread().name} 进行世界加载操作")
            //拉取世界数据
            val worldData = database.getWorldData(owner)
            if (worldData == null){
                logger.warning("§aPixelWorldPro $owner ${lang.getString("world.warning.load.noWorldData")}")
                future.complete(false)
                return@submit
            }
            //解压世界数据
            Zip.unzip(worldData.world, worldData.world)
            //加载世界
            val worldCreator = WorldCreator("PixelWorldPro/${worldData.world}/world")
            val world = Bukkit.createWorld(worldCreator)
            if (world == null) {
                future.complete(false)
                return@submit
            }
            localWorld[worldData.id] = world
            World.setLock(worldData.id)
            world.keepSpawnInMemory = false
            WorldSuccess.loadWorldSuccess(worldData)
            future.complete(true)
        }
        return future
    }

    override fun unloadWorld(id: Int): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        submit(async = asyncLoad) {
            logger.info("§aPixelWorldPro 使用线程：${Thread.currentThread().name} 进行世界卸载操作")
            //拉取世界数据
            val worldData = database.getWorldData(id)
            if (worldData == null){
                logger.warning("§aPixelWorldPro $id ${lang.getString("world.warning.unload.noWorldData")}")
                future.complete(false)
                return@submit
            }
            //获取世界
            val world = localWorld[worldData.id]
            if (world == null) {
                localWorld.remove(worldData.id)
                future.complete(true)
                return@submit
            }
            for (player in world.players){
                player.teleport(Bukkit.getWorld(worldConfig.getString("Unload.world")?: "world")!!.spawnLocation)
            }
            backupWorld(worldData.id, true)
            if (Bukkit.unloadWorld(world, true)){
                localWorld.remove(worldData.id)
                World.removeLock(worldData.id)
                if (bungee.getBoolean("Enable")){
                    com.mcyzj.pixelworldpro.bungee.System.removeWorldLock(worldData)
                }
                WorldSuccess.unloadWorldSuccess(worldData)
                Thread{
                    sleep(30000)
                    File(fileConfig.getString("World.Server"), worldData.world).deleteRecursively()
                }.start()
                future.complete(true)
            }else{
                future.complete(false)
            }
        }
        return future
    }

    override fun unloadWorld(owner: UUID): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        submit(async = asyncLoad) {
            logger.info("§aPixelWorldPro 使用线程：${Thread.currentThread().name} 进行世界卸载操作")
            //拉取世界数据
            val worldData = database.getWorldData(owner)
            if (worldData == null){
                logger.warning("§aPixelWorldPro $owner ${lang.getString("world.warning.unload.noWorldData")}")
                future.complete(false)
                return@submit
            }
            //获取世界
            val world = localWorld[worldData.id]
            if (world == null) {
                localWorld.remove(worldData.id)
                future.complete(true)
                return@submit
            }
            for (player in world.players){
                player.teleport(Bukkit.getWorld(worldConfig.getString("Unload.world")?: "world")!!.spawnLocation)
            }
            backupWorld(worldData.id, true)
            if (Bukkit.unloadWorld(world, true)){
                localWorld.remove(worldData.id)
                World.removeLock(worldData.id)
                if (bungee.getBoolean("Enable")){
                    com.mcyzj.pixelworldpro.bungee.System.removeWorldLock(worldData)
                }
                WorldSuccess.unloadWorldSuccess(worldData)
                Thread{
                    sleep(30000)
                    File(fileConfig.getString("World.Server"), worldData.world).deleteRecursively()
                }.start()
                future.complete(true)
            }else{
                future.complete(false)
            }
        }
        return future
    }

    override fun backupWorld(id: Int, save: Boolean?) {
        val isSave = save ?: true
        //拉取世界数据
        val worldData = database.getWorldData(id)
        if (worldData == null){
            logger.warning("§aPixelWorldPro $id ${lang.getString("world.warning.unload.noWorldData")}")
            return
        }
        //备份世界文件
        val world = localWorld[worldData.id]
        if (world == null) {
            localWorld.remove(worldData.id)
            return
        }
        val future = CompletableFuture<Boolean>()
        submit {
            if (isSave) {
                world.save()
            }
            future.complete(true)
        }
        future.thenApply {
            val time = System.currentTimeMillis()
            val date = Date(time)
            //把time时间格式化
            val formatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
            //把time时间格式化为字符串
            val timeString = formatter.format(date)
            Zip.toZip(worldData.world, worldData.world)
            val zip = File(fileConfig.getString("World.Path"), "/${worldData.world}/${worldData.world}.zip")
            val file = File(fileConfig.getString("World.Path")!!, worldData.world)
            val backup = File(file, "backup/$timeString")
            backup.mkdirs()
            zip.copyTo(File(backup, "world.zip"))
            //备份数据文件
            File(file, "config").copyRecursively(backup)
            val json = File(backup, "database.json")
            json.createNewFile()
            val fileWriter = FileWriter(json.absoluteFile, false)
            val bw = BufferedWriter(fileWriter)
            bw.write(database.joinToJson(worldData).toString())
            bw.close()
            //删除过期的备份
            val backupFile = File(file, "backup")
            if (fileConfig.getInt("Backup.number") < (backupFile.listFiles()?.size ?: 1)) {
                for (files in backupFile.listFiles()!!) {
                    files.deleteRecursively()
                    if (fileConfig.getInt("Backup.number") >= (backupFile.listFiles()?.size ?: 1)) {
                        break
                    }
                }
            }
            WorldSuccess.backupWorldSuccess(worldData, save)
        }
    }

    override fun backupWorld(owner: UUID, save: Boolean?) {
        val isSave = save ?: true
        //拉取世界数据
        val worldData = database.getWorldData(owner)
        if (worldData == null){
            logger.warning("§aPixelWorldPro $owner ${lang.getString("world.warning.unload.noWorldData")}")
            return
        }
        //备份世界文件
        val world = localWorld[worldData.id]
        if (world == null) {
            localWorld.remove(worldData.id)
            return
        }
        val future = CompletableFuture<Boolean>()
        submit {
            if (isSave) {
                world.save()
            }
            future.complete(true)
        }
        future.thenApply {
            val time = System.currentTimeMillis()
            val date = Date(time)
            //把time时间格式化
            val formatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
            //把time时间格式化为字符串
            val timeString = formatter.format(date)
            Zip.toZip(worldData.world, worldData.world)
            val zip = File(fileConfig.getString("World.Path"), "/${worldData.world}/${worldData.world}.zip")
            val file = File(fileConfig.getString("World.Path")!!, worldData.world)
            val backup = File(file, "backup/$timeString")
            backup.mkdirs()
            zip.copyTo(backup)
            //备份数据文件
            File(file, "config").copyRecursively(backup)
            val json = File(backup, "database.json")
            json.createNewFile()
            val fileWriter = FileWriter(json.absoluteFile, false)
            val bw = BufferedWriter(fileWriter)
            bw.write(database.joinToJson(worldData).toString())
            bw.close()
            //删除过期的备份
            val backupFile = File(file, "backup")
            if (fileConfig.getInt("Backup.number") < (backupFile.listFiles()?.size ?: 1)) {
                for (files in backupFile.listFiles()!!) {
                    files.deleteRecursively()
                    if (fileConfig.getInt("Backup.number") >= (backupFile.listFiles()?.size ?: 1)) {
                        break
                    }
                }
            }
            WorldSuccess.backupWorldSuccess(worldData, save)
        }
    }

    override fun zipWorld(from: String, to: String) {

    }

    override fun unzipWorld(zip: String, to: String) {
        TODO("Not yet implemented")
    }
}