package com.mcyzj.pixelworldpro.data.dataclass

import java.util.UUID

data class WorldData(
    //世界ID
    var id: Int,
    //世界主人
    var owner: UUID,
    //世界名称
    var name: String,
    //世界文件名
    var world: String,
    //世界权限表
    var permission: HashMap<String, HashMap<String, String>>,
    //玩家权限表
    var player: HashMap<UUID, String>,
    //世界维度表
    var dimension: HashMap<String, Boolean>
)
data class WorldCreateData(
    //世界主人
    var owner: UUID,
    //世界名称
    var name: String,
    //世界文件名
    var world: String,
    //世界权限表
    var permission: HashMap<String, HashMap<String, String>>,
    //玩家权限表
    var player: HashMap<UUID, String>,
    //世界维度表
    var dimension: HashMap<String, Boolean>
)