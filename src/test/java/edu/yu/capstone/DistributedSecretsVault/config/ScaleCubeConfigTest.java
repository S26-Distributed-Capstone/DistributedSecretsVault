package edu.yu.capstone.DistributedSecretsVault.config;

import io.scalecube.net.Address;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class ScaleCubeConfigTest {

    @Test
    void resolveSeedMembersBuildsAddressListFromAllDnsResults() throws Exception {
        ScaleCubeConfig.DnsResolver resolver = host -> new InetAddress[] {
                InetAddress.getByName("10.0.0.10"),
                InetAddress.getByName("10.0.0.11")
        };

        Address[] resolved = ScaleCubeConfig.resolveSeedMembers("dsv-app-headless.default.svc.cluster.local", 4801, resolver);

        Address[] expected = new Address[] {
                Address.from("10.0.0.10:4801"),
                Address.from("10.0.0.11:4801")
        };
        assertEquals(2, resolved.length);
        assertArrayEquals(expected, resolved);
    }

    @Test
    void resolveSeedMembersWithRetryRetriesThenSucceeds() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        ScaleCubeConfig.DnsResolver resolver = host -> {
            if (attempts.incrementAndGet() < 3) {
                throw new UnknownHostException(host);
            }
            return new InetAddress[] { InetAddress.getByName("10.0.0.12") };
        };

        Address[] resolved = ScaleCubeConfig.resolveSeedMembersWithRetry(
                "dsv-app-headless.default.svc.cluster.local", 4801, 3, 1, resolver);

        assertEquals(3, attempts.get());
        assertArrayEquals(new Address[] { Address.from("10.0.0.12:4801") }, resolved);
    }

    @Test
    void resolveSeedMembersWithRetryFailsWhenDnsNeverResolves() {
        ScaleCubeConfig.DnsResolver resolver = host -> {
            throw new UnknownHostException(host);
        };

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ScaleCubeConfig.resolveSeedMembersWithRetry("missing-host.default.svc.cluster.local", 4801, 2, 1,
                        resolver));

        assertTrue(ex.getMessage().contains("Unable to resolve ScaleCube seed members"));
        assertTrue(ex.getCause() instanceof IllegalStateException);
    }
}
