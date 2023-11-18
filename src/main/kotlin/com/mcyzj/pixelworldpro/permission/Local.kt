package com.mcyzj.pixelworldpro.permission

import com.mcyzj.pixelworldpro.PixelWorldPro
import com.mcyzj.pixelworldpro.data.dataclass.*
import com.mcyzj.pixelworldpro.expansion.core.level.user.User
import com.mcyzj.pixelworldpro.file.Config
import com.xbaimiao.easylib.bridge.economy.PlayerPoints
import com.xbaimiao.easylib.bridge.economy.Vault
import com.xbaimiao.easylib.module.item.hasItem
import com.xbaimiao.easylib.module.item.takeItem
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.collections.HashMap

object Local {
    private var config = Config.permission
    private val database = PixelWorldPro.databaseApi
    private var worldConfig = Config.world
    private var lang = PixelWorldPro.instance.lang
    private fun buildPermission(): HashMap<String, PermissionData> {
        //循环依次构建权限组
        val permissionMap = HashMap<String, PermissionData>()
        for (permission in config.getConfigurationSection("Permission")!!.getKeys(false)){
            val permissionConfig = config.getConfigurationSection("Permission.$permission")!!
            //循环构建不同权限组的数据
            val groupMap = HashMap<String, PermissionGroupData>()
            for (group in permissionConfig.getKeys(false)){
                val levelMap = HashMap<Int, PermissionUpData>()
                //构建基础值
                val groupConfig = permissionConfig.getConfigurationSection(group)!!
                val least = groupConfig.getInt("Least")
                val max = groupConfig.getInt("Max")
                //构建扩展槽消耗
                val useConfig = groupConfig.getConfigurationSection("Use")!!
                var level = 0
                var lastLevel = useConfig.getKeys(false).first().toInt()
                //构建第一个扩展消耗数据
                val firstLevelConfig = useConfig.getConfigurationSection(lastLevel.toString())!!
                val firstItemConfig = firstLevelConfig.getConfigurationSection("Item")
                val firstItemMap = HashMap<String, Int>()
                if (firstItemConfig != null) {
                    if (firstItemConfig.getKeys(false).isNotEmpty()) {
                        for (key in firstItemConfig.getKeys(false)) {
                            firstItemMap[key as String] = firstItemConfig.getInt(key)
                        }
                    }
                }
                val firstPermissionUpData = PermissionUpData(
                    firstLevelConfig.getDouble("Points"),
                    firstLevelConfig.getDouble("Money"),
                    firstItemMap
                )
                levelMap[lastLevel] = firstPermissionUpData
                //构建剩余扩展
                while (level <= max){
                    if (level.toString() in useConfig.getKeys(false)) {
                        val levelConfig = useConfig.getConfigurationSection(lastLevel.toString())!!
                        val itemConfig = levelConfig.getConfigurationSection("Item")
                        val itemMap = HashMap<String, Int>()
                        if (itemConfig != null) {
                            if (itemConfig.getKeys(false).isNotEmpty()) {
                                for (key in itemConfig.getKeys(false)) {
                                    itemMap[key as String] = itemConfig.getInt(key)
                                }
                            }
                        }
                        val permissionUpData = PermissionUpData(
                            levelConfig.getDouble("Points"),
                            levelConfig.getDouble("Money"),
                            itemMap
                        )
                        levelMap[level] = permissionUpData
                        lastLevel = level
                    } else {
                        levelMap[level] = levelMap[lastLevel]!!
                    }
                    level ++
                }
                val permissionGroupData = PermissionGroupData(
                    least,
                    max,
                    levelMap
                )
                groupMap[group] = permissionGroupData
            }
            val permissionData = PermissionData(
                permission,
                groupMap
            )
            permissionMap[permission] = permissionData
        }
        return permissionMap
    }

    fun setGroup(worldData: WorldData, player: OfflinePlayer, permission: String): ResultData {
        val worldPermissionData = worldData.permission[permission] ?: return ResultData(false, (lang.getString("world.warning.permission.set.noGroup") ?: "无法找到对应世界内权限组名：") + permission)
        val playerMap = worldData.player
        val worldPermissionConfig =
            Config.getWorldConfig(worldData).getConfigurationSection("Permission") ?: YamlConfiguration()
        val permissionMap = buildPermission()
        var number = 0
        //循环历遍获取已经有的人数
        for (playerPermission in playerMap.values) {
            if (playerPermission == permission) {
                number++
            }
        }
        val permissionData = permissionMap[permission] ?: permissionMap["Default"]!!
        var playerPermission = worldPermissionConfig.getString("Permission")?:"Default"
        val owner = Bukkit.getPlayer(worldData.owner)
        if (owner != null) {
            for (key in permissionData.group.keys) {
                if (owner.hasPermission(key)) {
                    playerPermission = key
                }
            }
        }
        worldPermissionConfig.set("Permission", playerPermission)
        var max = worldPermissionConfig.getInt("$permission.Max")
        if (max <= 1) {
            max = permissionData.group[playerPermission]!!.least
            worldPermissionConfig.set("$permission.Max", max)
        }
        if (max < permissionData.group[playerPermission]!!.least){
            max = permissionData.group[playerPermission]!!.least
            worldPermissionConfig.set("$permission.Max", max)
        }
        val config = Config.getWorldConfig(worldData)
        config.set("Permission", worldPermissionConfig)
        Config.saveWorldConfig(worldData, config)
        if (max <= number){
            return ResultData(
                false,
                lang.getString("world.warning.permission.set.max") ?: "已达到该权限组当前人数上限"
            )
        }
        worldData.player[player.uniqueId] = permission
        database.setWorldData(worldData)
        if (player.isOnline) {
            checkPlayerPermission(player as Player, worldPermissionData, worldData)
        }
        return ResultData(
            true,
            lang.getString("world.info.permission.set.success") ?: "成功设置玩家在世界内的权限组"
        )
    }

    private fun checkPlayerPermission(player: Player, permissionData: HashMap<String, String>, worldData: WorldData){
        if (player.world.name.contains(worldData.world)){
            if (permissionData["Teleport"] == "false"){
                player.teleport(Bukkit.getWorld(worldConfig.getString("Unload.world")?: "world")!!.spawnLocation)
                return
            }
            when (permissionData["GameMode"]){
                "ADVENTURE" -> {
                    player.gameMode = GameMode.ADVENTURE
                }

                "SURVIVAL" -> {
                    player.gameMode = GameMode.SURVIVAL
                }

                "CREATIVE" -> {
                    player.gameMode = GameMode.CREATIVE
                }

                "SPECTATOR" -> {
                    player.gameMode = GameMode.SPECTATOR
                }
            }
            when (permissionData["Fly"]){
                "false" -> {
                    player.allowFlight = false
                }
                "true" -> {
                    player.allowFlight = true
                }
            }
        }
    }

    fun upPermission(worldData: WorldData, permission: String): ResultData {
        //检查操作是否合法
        worldData.permission[permission]
            ?: return ResultData(false,
                (lang.getString("world.warning.permission.add.noGroup") ?: "无法找到对应世界内权限组名：") + permission)
        val worldPermissionConfig =
            Config.getWorldConfig(worldData).getConfigurationSection("Permission") ?: YamlConfiguration()
        val permissionMap = buildPermission()
        val permissionData = permissionMap[permission] ?: permissionMap["Default"]!!
        var playerPermission = worldPermissionConfig.getString("Permission")?:"Default"
        val owner = Bukkit.getPlayer(worldData.owner)!!
        for (key in permissionData.group.keys) {
            if (owner.hasPermission(key)) {
                playerPermission = key
            }
        }
        worldPermissionConfig.set("Permission", playerPermission)
        var max = worldPermissionConfig.getInt("$permission.Max")
        if (max <= 1) {
            max = permissionData.group[playerPermission]!!.least
            if (max + 1 > permissionData.group[playerPermission]!!.max){
                worldPermissionConfig.set("$permission.Max", max)
                Config.saveWorldConfig(worldData, config)
                return ResultData(
                    false,
                    lang.getString("world.warning.permission.add.max") ?: "已达到该权限组最大人数上限"
                )
            }
            worldPermissionConfig.set("$permission.Max", max + 1)
        }
        if (max < permissionData.group[playerPermission]!!.least){
            max = permissionData.group[playerPermission]!!.least
        }
        if (max + 1 >= permissionData.group[playerPermission]!!.max){
            return ResultData(
                false,
                lang.getString("world.warning.permission.add.max") ?: "已达到该权限组最大人数上限"
            )
        }
        //检查是否满足升级条件
        val upData = permissionData.group[playerPermission]!!.level[max]!!
        //检验经济
        if (upData.points > 0.0) {
            if (!PlayerPoints().has(owner, upData.points)) {
                var msg = lang.getString("world.warning.permission.add.points") ?: "没有足够的点券，你需要{0}个点券，你只有{1}个点券"
                msg = msg.replace("{0}", upData.points.toString())
                msg = msg.replace("{1}", PlayerPoints()[owner].toString())
                return ResultData(
                    false,
                    msg
                )
            }
        }
        if (upData.money > 0.0) {
            if (!Vault().has(owner, upData.money)) {
                var msg = lang.getString("world.warning.permission.add.money")
                    ?: "没有足够的金币，你需要{0}个金币，你只有{1}个金币"
                msg = msg.replace("{0}", upData.money.toString())
                msg = msg.replace("{1}", Vault()[owner].toString())
                return ResultData(
                    false,
                    msg
                )
            }
        }
        //检验物品
        val itemMap = upData.item
        for (key in itemMap.keys){
            val itemData = getItemData(key)
            val material = Material.getMaterial(itemData.material)!!
            val item = ItemStack(material)
            for (lore in itemData.lore){
                item.lore?.add(lore)
            }
            if (!owner.inventory.hasItem(item, itemMap[key]!!)) {
                var msg = lang.getString("world.warning.permission.add.item") ?: "没有足够的物品，你需要{0}个{1}"
                msg = msg.replace("{0}", itemMap[key]!!.toString())
                msg = msg.replace("{1}", material.name)
                return ResultData(
                    false,
                    msg
                )
            }
        }
        //拿走物品
        for (key in itemMap.keys) {
            val itemData = getItemData(key)
            owner.inventory.takeItem(itemMap[key]!!) {
                return@takeItem this.type == Material.getMaterial(itemData.material)!!
            }
        }
        if (upData.points > 0.0) {
            PlayerPoints().take(owner, upData.points)
        }
        if (upData.money > 0.0) {
            Vault().take(owner, upData.money)
        }
        //进行升级操作
        worldPermissionConfig.set("$permission.Max", max + 1)
        val config = Config.getWorldConfig(worldData)
        config.set("Permission", worldPermissionConfig)
        Config.saveWorldConfig(worldData, config)
        return ResultData(
            true,
            lang.getString("world.info.permission.add.success") ?: "成功扩展世界内权限组玩家槽位"
        )
    }

    private fun getItemData(name: String): ItemData {
        val material = config.getString("Item.$name.Material")!!.uppercase(Locale.getDefault())
        val lore = config.getStringList("Item.$name.Lore")
        return ItemData(
            material,
            lore
        )
    }
}