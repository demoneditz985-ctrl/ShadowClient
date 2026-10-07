package com.project.vortex.client.game.module.impl.motion

import com.project.vortex.client.R
import com.project.vortex.client.game.InterceptablePacket
import com.project.vortex.client.constructors.Element
import com.project.vortex.client.constructors.CheatCategory
import com.project.vortex.client.game.utils.math.MathUtil
import com.project.vortex.client.util.AssetManager
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket
import kotlin.math.cos
import kotlin.math.sin

class JetPackElement(iconResId: Int = AssetManager.getAsset("ic_ethereum_black_24dp")) : Element(
    name = "Jetpack",
    category = CheatCategory.Motion,
    iconResId,
    displayNameResId = AssetManager.getString("module_jet_pack_display_name")
) {
    private var speed by floatValue("Speed", 0.5f, 0.1f..1.5f)

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled) {
            return
        }

        val packet = interceptablePacket.packet

        if (packet is PlayerAuthInputPacket) {
            val motionPacket = SetEntityMotionPacket().apply {
                runtimeEntityId = session.localPlayer.runtimeEntityId
                motion = MathUtil.getMovementDirectionRotDeg(packet.rotation, speed)
            }
            session.clientBound(motionPacket)
        }
    }
}