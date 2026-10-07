package com.project.vortex.client.game.module.impl.visual

import com.project.vortex.client.R
import com.project.vortex.client.constructors.CheatCategory
import com.project.vortex.client.constructors.Element
import com.project.vortex.client.game.InterceptablePacket
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket
import com.project.vortex.client.util.AssetManager

class NoFireElement : Element(
    name = "NoFire",
    category = CheatCategory.Visual,
    displayNameResId = AssetManager.getString("module_nofire")
) {

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled) return

       val packet = interceptablePacket.packet

        if(packet is SetEntityDataPacket){
            if(packet.runtimeEntityId == session.localPlayer.runtimeEntityId){
                if(packet.metadata.flags.contains(EntityFlag.ON_FIRE)){
                    packet.metadata.flags.remove(EntityFlag.ON_FIRE)
                }
            }
        }

        }



}