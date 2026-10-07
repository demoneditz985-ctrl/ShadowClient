package com.project.vortex.relay.listener

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket

interface VortexRelayPacketListener {

    fun beforeClientBound(packet: BedrockPacket): Boolean {
        return false
    }

    fun beforeServerBound(packet: BedrockPacket): Boolean {
        return false
    }

    fun afterClientBound(packet: BedrockPacket) {}

    fun afterServerBound(packet: BedrockPacket) {}

    fun onDisconnect(reason: String) {

    }

}