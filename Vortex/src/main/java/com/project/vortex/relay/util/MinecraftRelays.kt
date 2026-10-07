package com.project.vortex.relay.util

import com.project.vortex.relay.VortexRelay
import com.project.vortex.relay.VortexRelaySession
import com.project.vortex.relay.address.VortexAddress
import org.cloudburstmc.protocol.bedrock.BedrockPong

/**
 * Creates a VortexRelay instance in capture mode that will connect to the specified server.
 * This function automatically sets up the relay to capture packets between the client and server.
 * 
 * @param advertisement The server advertisement details used for client connections
 * @param localAddress The local address the relay will bind to
 * @param remoteAddress The remote server address to connect to
 * @param onSessionCreated Callback executed when a relay session is created
 * @return A configured VortexRelay instance
 */
fun captureVortexRelay(
    advertisement: BedrockPong = VortexRelay.createNativeAdvertisement(),
    localAddress: VortexAddress = VortexAddress("0.0.0.0", 19132),
    remoteAddress: VortexAddress,
    onSessionCreated: VortexRelaySession.() -> Unit
): VortexRelay {
    return VortexRelay(
        localAddress = localAddress,
        advertisement = advertisement
    ).capture(
        remoteAddress = remoteAddress,
        onSessionCreated = onSessionCreated
    )
}