package de.lumiwork.silentac;

import java.util.EnumMap;
import java.util.Map;

public final class PlayerData {

    public enum CheckType { SPEED, REACH }

    private final Map<CheckType, Double> vl = new EnumMap<>(CheckType.class);
    private final Map<CheckType, Double> buffer = new EnumMap<>(CheckType.class);
    private final Map<CheckType, Long> lastAlert = new EnumMap<>(CheckType.class);

    public int exemptUntilTick;
    public int lastAirTick;
    public int lastIceTick;
    public int lastBlockAboveTick;
    public int lastLiquidTick;
    public int lastBounceTick;
    public boolean alertsEnabled = true;

    public double vl(CheckType type) {
        return vl.getOrDefault(type, 0.0);
    }

    public double addVl(CheckType type, double amount) {
        double value = vl(type) + amount;
        vl.put(type, value);
        return value;
    }

    public double buffer(CheckType type) {
        return buffer.getOrDefault(type, 0.0);
    }

    public double addBuffer(CheckType type, double amount) {
        double value = Math.max(0, buffer(type) + amount);
        buffer.put(type, value);
        return value;
    }

    public void resetBuffer(CheckType type) {
        buffer.put(type, 0.0);
    }

    public boolean alertReady(CheckType type, long now, long cooldownMs) {
        if (now - lastAlert.getOrDefault(type, 0L) < cooldownMs) {
            return false;
        }
        lastAlert.put(type, now);
        return true;
    }

    public void decay(double amount) {
        vl.replaceAll((type, value) -> Math.max(0, value - amount));
    }

    public void reset() {
        vl.clear();
        buffer.clear();
    }
}
