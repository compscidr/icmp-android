package com.jasonernst.icmp_common.v4

import com.jasonernst.icmp_common.ICMPHeader
import com.jasonernst.icmp_common.PacketHeaderException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Implementation of ICMP header:
 * https://en.wikipedia.org/wiki/Internet_Control_Message_Protocol
 */
abstract class ICMPv4Header(
    icmpV4Type: ICMPv4Type,
    code: UByte,
    checksum: UShort,
) : ICMPHeader(type = icmpV4Type, code = code, checksum = checksum) {
    companion object {
        fun fromStream(buffer: ByteBuffer, limit: Int = buffer.remaining(), icmpV4Type: ICMPv4Type, code: UByte, checksum: UShort, order: ByteOrder = ByteOrder.BIG_ENDIAN): ICMPv4Header {
            return when (icmpV4Type) {
                ICMPv4Type.ECHO_REPLY, ICMPv4Type.ECHO_REQUEST -> {
                    ICMPv4EchoPacket.fromStream(buffer, limit, icmpV4Type, checksum, order)
                }
                ICMPv4Type.DESTINATION_UNREACHABLE -> {
                    ICMPv4DestinationUnreachablePacket.fromStream(buffer, limit, code, checksum, order)
                }
                ICMPv4Type.TIME_EXCEEDED -> {
                    ICMPv4TimeExceededPacket.fromStream(buffer, limit, code, checksum, order)
                }
                else -> {
                    throw PacketHeaderException("Unsupported ICMPv4 type")
                }
            }
        }
    }
}