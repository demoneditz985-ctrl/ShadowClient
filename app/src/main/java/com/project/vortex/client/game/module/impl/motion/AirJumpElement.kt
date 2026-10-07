package com.project.vortex.client.game.module.impl.motion

import com.project.vortex.client.R
import com.project.vortex.client.game.InterceptablePacket
import com.project.vortex.client.constructors.Element
import com.project.vortex.client.constructors.CheatCategory
import com.project.vortex.client.util.AssetManager
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket

class AirJumpElement(iconResId: Int = AssetManager.getAsset("ic_cloud_upload_black_24dp")) : Element(
    name = "AirJump",
    category = CheatCategory.Motion,
    iconResId,
    displayNameResId = AssetManager.getString("module_air_jump_display_name")
) {

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled) {
            return
        }

        val packet = interceptablePacket.packet

        if (packet is PlayerAuthInputPacket) {
            if (packet.inputData.contains(PlayerAuthInputData.JUMP_DOWN)) {
                val motionPacket = SetEntityMotionPacket().apply {
                    runtimeEntityId = session.localPlayer.runtimeEntityId
                    motion = Vector3f.from(
                        session.localPlayer.motionX,
                        0.42f,
                        session.localPlayer.motionZ
                    )
                }
                session.clientBound(motionPacket)
            }
        }
    }
}