package com.jasonernst.icmp_android

import android.system.ErrnoException
import com.jasonernst.icmp_common.PingResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class AndroidPingTest {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val icmp = ICMPAndroid

    companion object {
        // assumes this is unreachable from the android phone
        const val UNREACHABLE_IPv4 = "180.171.171.171"

        // should always be reachable
        const val REACHABLE_IPv4 = "127.0.0.1"
    }

    @Test
    fun pingIpv4Localhost() {
        runBlocking {
            icmp.ping("localhost")
        }
    }

    @Test fun pingIpv6Localhost() {
        runBlocking {
            icmp.ping("::1")
        }
    }

    @Test
    fun pingReachableIpv4() {
        runBlocking {
            icmp.ping(REACHABLE_IPv4)
        }
    }

    @Test fun pingUnreachableIpv4() {
        runBlocking {
            val result = icmp.ping(UNREACHABLE_IPv4)
            assertTrue(result is PingResult.Failed)
        }
    }

    @Test fun pingUnknownHost() {
        runBlocking {
            assertThrows<UnknownHostException> {
                icmp.ping("dfadfasdf.com")
            }
        }
    }

    @Test fun pingTimeout() {
        runBlocking {
            // first do a ping with a normal timeout to make sure the host works
            var result = icmp.ping("8.8.8.8")
            assertTrue(result is PingResult.Success)

            // then do one with an aggressive timeout
            result = icmp.ping("8.8.8.8", pingTimeoutMS = 1)
            assertTrue(result is PingResult.Failed)
        }
    }

    @Test fun pingDnsTimeout() {
        runBlocking {
            assertThrows<TimeoutCancellationException> {
                // do a resolution to a host we haven't used yet so we don't get a cache hit
                icmp.ping("jasonernst.com", resolveTimeoutMS = 1)
            }
        }
    }

    @Test fun pingWithIdAndSequence() {
        runBlocking {
            icmp.ping(InetAddress.getLoopbackAddress(), id = 0x1234u, sequence = 0x5678u)
        }
    }
}