package edu.yu.capstone.DistributedSecretsVault.util;

public final class ClockUtil {
    private ClockUtil() {
    }

    public static long nowEpochMillis() {
        return System.currentTimeMillis();
    }
}
