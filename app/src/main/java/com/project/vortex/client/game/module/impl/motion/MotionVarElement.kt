package com.project.vortex.client.game.module.impl.motion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.project.vortex.client.game.InterceptablePacket
import com.project.vortex.client.constructors.Element
import com.project.vortex.client.constructors.CheatCategory
import com.project.vortex.client.util.AssetManager
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket

class MotionVarElement : Element("_var_", CheatCategory.Motion) {

    init {
        isEnabled = true
    }

    companion object {
        var lastUpdateAbilitiesPacket: UpdateAbilitiesPacket? by mutableStateOf(null)
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (interceptablePacket.packet is UpdateAbilitiesPacket) {
            lastUpdateAbilitiesPacket = interceptablePacket.packet
        }
    }

}