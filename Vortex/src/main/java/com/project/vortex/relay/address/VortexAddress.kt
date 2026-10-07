package com.project.vortex.relay.address

import java.net.InetSocketAddress

data class VortexAddress(val hostName: String, val port: Int)

inline val VortexAddress.inetSocketAddress
    get() = InetSocketAddress(hostName, port)

inline val InetSocketAddress.vortexAddress
    get() = VortexAddress(hostName, port)