package com.jasonernst.icmp_common.v6

import com.jasonernst.icmp_common.ICMPHeader
import com.jasonernst.icmp_common.PacketHeaderException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Implementation of ICMPv6 header.
 *
 * Only difference is the protocol number it returns, and the ICMPTypes it uses.
 *
 * https://en.wikipedia.org/wiki/Internet_Control_Message_Protocol
 */
abstract class ICMPv6Header(
    icmPv6Type: ICMPv6Type,
    code: UByte,
    checksum: UShort
) : ICMPHeader(type = icmPv6Type, code = code, checksum = checksum) {
    companion object {
        fun fromStream(buffer: ByteBuffer, limit: Int = buffer.remaining(), icmpV6Type: ICMPv6Type, code: UByte, checksum: UShort, order: ByteOrder = ByteOrder.BIG_ENDIAN): ICMPv6Header {
            return when (icmpV6Type) {
                ICMPv6Type.ECHO_REPLY_V6, ICMPv6Type.ECHO_REQUEST_V6 -> {
                    ICMPv6EchoPacket.fromStream(buffer, limit, icmpV6Type, checksum, order)
                }
                ICMPv6Type.DESTINATION_UNREACHABLE -> {
                    ICMPv6DestinationUnreachablePacket.fromStream(buffer, limit, code, checksum, order)
                }
                ICMPv6Type.TIME_EXCEEDED -> {
                    ICMPv6TimeExceededPacket.fromStream(buffer, limit, code, checksum, order)
                }
                ICMPv6Type.MULTICAST_LISTENER_DISCOVERY_V2 -> {
                    ICMPv6MulticastListenerDiscoveryV2.fromStream(buffer, limit, checksum, order)
                }
                ICMPv6Type.ROUTER_SOLICITATION_V6 -> {
                    ICMPv6RouterSolicitationPacket.fromStream(buffer, limit, checksum, order)
                }
                ICMPv6Type.ROUTER_ADVERTISEMENT_V6 -> {
                    ICMPv6RouterAdvertisementPacket.fromStream(buffer, limit, checksum, order)
                }
                else -> {
                    throw PacketHeaderException("Unsupported ICMPv6 type")
                }
            }
        }
    }
}