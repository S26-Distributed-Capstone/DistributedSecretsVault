package edu.yu.capstone.DistributedSecretsVault.util;

public final class NetworkUtil {
    private NetworkUtil() {
    }

    public static String toNodeId(String host, int port) {
        return host + ":" + port;
    }
}
