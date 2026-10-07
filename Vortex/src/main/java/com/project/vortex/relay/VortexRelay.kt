package com.project.vortex.relay

import com.project.vortex.relay.VortexRelaySession.ClientSession
import com.project.vortex.relay.address.VortexAddress
import com.project.vortex.relay.address.inetSocketAddress
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerRateLimiter
import org.cloudburstmc.protocol.bedrock.BedrockPeer
import org.cloudburstmc.protocol.bedrock.BedrockPong
import org.cloudburstmc.protocol.bedrock.PacketDirection
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec
import org.cloudburstmc.protocol.bedrock.codec.v786.Bedrock_v786
import org.cloudburstmc.protocol.bedrock.codec.v818.Bedrock_v818
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer
import kotlin.random.Random

class VortexRelay(
    private val localAddress: VortexAddress = VortexAddress(getNativeDefaultIp(), getNativeDefaultPort()),
    private val advertisement: BedrockPong = createNativeAdvertisement()
) {

    companion object {
        val DefaultCodec: BedrockCodec = Bedrock_v818.CODEC

        
        init {
            System.loadLibrary("vortex")
        }

        
        @JvmStatic external fun getNativeDefaultIp(): String
        @JvmStatic external fun getNativeDefaultPort(): Int
        @JvmStatic external fun getNativeRemoteIp(): String
        @JvmStatic external fun getNativeRemotePort(): Int
        @JvmStatic external fun createNativeAdvertisement(): BedrockPong
    }

    val isRunning: Boolean
        get() = channelFuture != null

    private var channelFuture: ChannelFuture? = null
    private var vortexRelaySession: VortexRelaySession? = null
    private var remoteAddress: VortexAddress? = null
    private val eventLoopGroup: EventLoopGroup = NioEventLoopGroup()

    fun capture(
        remoteAddress: VortexAddress = VortexAddress(getNativeRemoteIp(), getNativeRemotePort()),
        onSessionCreated: VortexRelaySession.() -> Unit
    ): VortexRelay {
        if (isRunning) {
            return this
        }

        this.remoteAddress = remoteAddress

        advertisement
            .ipv4Port(localAddress.port)
            .ipv6Port(localAddress.port)

        ServerBootstrap()
            .group(eventLoopGroup)
            .channelFactory(RakChannelFactory.server(NioDatagramChannel::class.java))
            .option(RakChannelOption.RAK_ADVERTISEMENT, advertisement.toByteBuf())
            .option(RakChannelOption.RAK_GUID, Random.nextLong())
            .childHandler(object : BedrockChannelInitializer<VortexRelaySession.ServerSession>() {
                override fun createSession0(peer: BedrockPeer, subClientId: Int): VortexRelaySession.ServerSession {
                    return VortexRelaySession(peer, subClientId, this@VortexRelay)
                        .also {
                            vortexRelaySession = it
                            it.onSessionCreated()
                        }
                        .server
                }

                override fun initSession(session: VortexRelaySession.ServerSession) {}
                override fun preInitChannel(channel: Channel) {
                    channel.attr(PacketDirection.ATTRIBUTE).set(PacketDirection.CLIENT_BOUND)
                    super.preInitChannel(channel)
                }
            })
            .localAddress(localAddress.inetSocketAddress)
            .bind()
            .awaitUninterruptibly()
            .also {
                it.channel().pipeline().remove(RakServerRateLimiter.NAME)
                channelFuture = it
            }

        return this
    }

    internal fun connectToServer(onSessionCreated: ClientSession.() -> Unit) {
        val clientGUID = Random.nextLong()

        Bootstrap()
            .group(eventLoopGroup)
            .channelFactory(RakChannelFactory.client(NioDatagramChannel::class.java))
            .option(RakChannelOption.RAK_PROTOCOL_VERSION, vortexRelaySession!!.server.codec.raknetProtocolVersion)
            .option(RakChannelOption.RAK_GUID, clientGUID)
            .option(RakChannelOption.RAK_REMOTE_GUID, clientGUID)
            .option(RakChannelOption.RAK_CONNECT_TIMEOUT, 690000)
            .handler(object : BedrockChannelInitializer<ClientSession>() {
                override fun createSession0(peer: BedrockPeer, subClientId: Int): ClientSession {
                    return vortexRelaySession!!.ClientSession(peer, subClientId)
                }

                override fun initSession(clientSession: ClientSession) {
                    vortexRelaySession!!.client = clientSession
                    onSessionCreated(clientSession)
                }

                override fun preInitChannel(channel: Channel) {
                    channel.attr(PacketDirection.ATTRIBUTE).set(PacketDirection.SERVER_BOUND)
                    super.preInitChannel(channel)
                }
            })
            .remoteAddress(remoteAddress!!.inetSocketAddress)
            .connect()
            .awaitUninterruptibly()
    }

    fun disconnect() {
        if (!isRunning) {
            return
        }

        channelFuture?.channel()?.also {
            it.close().awaitUninterruptibly()
            it.parent().close().awaitUninterruptibly()
        }
        channelFuture = null
        vortexRelaySession = null
    }
}