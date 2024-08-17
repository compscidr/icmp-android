package com.jasonernst.icmp_common
import com.jasonernst.icmp_common.v4.ICMPv4EchoPacket
import com.jasonernst.icmp_common.v6.ICMPv6EchoPacket
import com.jasonernst.packetdumper.stringdumper.StringPacketDumper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.io.FileDescriptor
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.math.E

abstract class ICMP {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val ICMP_PORT = 7

        // todo: confirm these match in android
        const val IPPROTO_IP = 0        // https://fossd.anu.edu.au/linux/v2.6.29-rc2/source/include/linux/in.h#L26
        const val IP_TOS = 1            // https://fossd.anu.edu.au/linux/v2.6.29-rc2/source/include/linux/in.h#L60
        const val IPPROTO_IPV6 = 41     // https://fossd.anu.edu.au/linux/v2.6.29-rc2/source/include/linux/in.h#L39
        const val IPV6_TCLASS = 67      // https://fossd.anu.edu.au/linux/v2.6.29-rc2/source/include/linux/in6.h#L248
    }
    abstract fun obtainSocketIpv4Socket(): FileDescriptor
    abstract fun obtainSocketIpv6Socket(): FileDescriptor
    //abstract fun setsocketlowDelay(fd: FileDescriptor)
    abstract fun setsockoptInt(fd: FileDescriptor, level: Int, optname: Int, optval: Int): Int
    abstract fun setsocketRecvTimeout(fd: FileDescriptor, timeoutMS: Long)
    abstract fun sendto(fd: FileDescriptor, buffer: ByteBuffer, flags: Int, address: InetAddress, port: Int): Int
    abstract fun recvfrom(fd: FileDescriptor, buffer: ByteBuffer, flags: Int, address: InetAddress, port: Int): Int

    /**
     * Pings a hostname - which could be an IP address or a domain name. There are two separate
     * timeouts here - one for the name resolution and one for the ping itself. Note that this
     * function will run at worst resolveTimeoutMS + pingTimeoutMS.
     *
     * Note: it looks like on both Android and Linux, the ID is set by the OS. We can however,
     * control the sequence number and use it to match on the recv side.
     */
    fun ping(host: String, resolveTimeoutMS: Long = 1000, pingTimeoutMS: Long = 1000, id: UShort = 0u, sequence: UShort = 0u, data: ByteArray = ByteArray(0)) {
        runBlocking {
            val inetAddress: InetAddress = withTimeout(resolveTimeoutMS) {
                withContext(Dispatchers.IO) {
                    InetAddress.getByName(host)
                }
            }
            logger.debug("Resolved $host to ${inetAddress.hostAddress}")
            ping(inetAddress, pingTimeoutMS, id, sequence)
        }
    }

    /**
     * Pings an InetAddress (Ipv4 or Ipv6) with a timeout. This is a blocking function that will
     * return when either a response has been received or the timeout has been reached.
     */
    fun ping(inetAddress: InetAddress, timeoutMS: Long = 1000, id: UShort, sequence: UShort, data: ByteArray = ByteArray(0)) {
        val fd = if (inetAddress is Inet4Address) {
            logger.debug("ping4 to ${inetAddress.hostAddress}")
            obtainSocketIpv4Socket()
        } else {
            logger.debug("ping6 to ${inetAddress.hostAddress}")
            obtainSocketIpv6Socket()
        }

        // can only call this function on android O and higher
        // not sure if these actually do much of anything, but putting them in anyway
        try {
            if (inetAddress is Inet4Address) {
                // https://datatracker.ietf.org/doc/html/rfc791 page 12,
                // setting bit3 to 1 means low delay
                setsockoptInt(fd, IPPROTO_IP, IP_TOS, 0b10000)
            } else {
                // https://en.wikipedia.org/wiki/Differentiated_services#Expedited_Forwarding
                setsockoptInt(fd, IPPROTO_IPV6, IPV6_TCLASS, 0b101110)
            }
        } catch (e: Exception) {
            logger.error("Failed to set socket into low delay mode: ${e.message}")
        }

        // can only call on android Q and higher
        setsocketRecvTimeout(fd, timeoutMS)

        // todo: construct an ICMP packet
        // https://man7.org/linux/man-pages/man2/sendto.2.html
        // this will return -1 on error and errno set
        val icmpHeader = if (inetAddress is Inet4Address) {
            ICMPv4EchoPacket(id = id, sequence = sequence, data = data)
        } else {
            ICMPv6EchoPacket(id = id, sequence = sequence, data = data)
        }
        logger.debug("request: $icmpHeader")
        val bytesToSend = ByteBuffer.wrap(icmpHeader.toByteArray())
        val stringDumper = StringPacketDumper()
        logger.debug("bytesToSend: ${icmpHeader.toByteArray().size}\n${stringDumper.dumpBufferToString(bytesToSend, 0 , bytesToSend.limit())}")
        val bytesSent = sendto(fd, bytesToSend, 0, inetAddress, ICMP_PORT)
        logger.debug("bytesSent: $bytesSent")

        val recvBuffer = ByteBuffer.allocate(1024)
        // may also be able to use null. We want to filter to only the address we sent it to so
        // someone else can't send us a response and confuse the result
        // https://man7.org/linux/man-pages/man2/recvfrom.2.html
        // this will return 0 when the socket is EOF and -1 on error with errno set
        val bytesRecieved = recvfrom(fd, recvBuffer, 0, inetAddress, ICMP_PORT)
        recvBuffer.limit(bytesRecieved)
        logger.debug("bytesRecieved: $bytesRecieved\n${stringDumper.dumpBufferToString(recvBuffer, 0, bytesRecieved)}")

        recvBuffer.position(0) // bug in the string dumping code
        val response = ICMPHeader.fromStream(recvBuffer, inetAddress is Inet4Address)
        logger.debug("response: $response")
        if (response is ICMPv4EchoPacket) {
            if (response.sequence != sequence) {
                throw Exception("Sequence number mismatch")
            }
        } else if (response is ICMPv6EchoPacket) {
            if (response.sequence != sequence) {
                throw Exception("Sequence number mismatch")
            }
        } else {
            throw Exception("Unknown ICMP response type")
        }
    }
}