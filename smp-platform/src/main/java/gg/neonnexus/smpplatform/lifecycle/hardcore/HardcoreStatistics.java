package gg.neonnexus.smpplatform.lifecycle.hardcore;

/** Snapshot values used for Obsidian Gate player views and leaderboards. */
public record HardcoreStatistics(
        long survivalSeconds, long daysSurvived, long deaths, long mobKills, long playerKills,
        long bossKills, long distanceTravelled, long blocksMined, long hardcorePoints, long bestSurvivalSeconds
) {
    public static HardcoreStatistics empty() { return new HardcoreStatistics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    public HardcoreStatistics withDeath(long survivalAtDeath) {
        return new HardcoreStatistics(survivalSeconds, daysSurvived, deaths + 1, mobKills, playerKills, bossKills,
                distanceTravelled, blocksMined, hardcorePoints, Math.max(bestSurvivalSeconds, survivalAtDeath));
    }
}
