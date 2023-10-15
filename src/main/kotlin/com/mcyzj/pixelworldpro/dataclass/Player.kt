package com.mcyzj.pixelworldpro.dataclass

import java.util.UUID

data class PlayerData(
    var uuid: UUID,
    var name: String,
    var invitation: HashMap<Int, String>
)