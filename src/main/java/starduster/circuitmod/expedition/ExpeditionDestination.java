package starduster.circuitmod.expedition;

/**
 * Available destinations for expeditions, each with different risk/reward profiles.
 */
public enum ExpeditionDestination {
    DUST_BELT_ALPHA("Dust Belt Alpha", 50, 3, 0.1, 1.0, "A nearby field of rocky debris. Safe but slim pickings."),
    IRON_RIDGE("Iron Ridge", 75, 5, 0.2, 1.3, "Dense metallic asteroids with reliable ore deposits."),
    CRYSTALLINE_DRIFT("Crystalline Drift", 100, 6, 0.3, 1.6, "Shimmering formations hide valuable crystals within."),
    HOLLOW_MOON_REMNANT("Hollow Moon Remnant", 120, 6, 0.35, 1.8, "Fragments of a shattered moon. Unstable but rich."),
    SULFUR_VENTS("Sulfur Vents", 140, 7, 0.4, 2.0, "Volcanic asteroid cluster. Corrosive but lucrative."),
    DARKSIDE_CLUSTER("Darkside Cluster", 175, 8, 0.5, 2.4, "Perpetually shadowed rocks hiding ancient deposits."),
    WRECKAGE_FIELD("Wreckage Field", 200, 9, 0.6, 2.8, "Ship graveyard from a forgotten war. Salvage aplenty."),
    CRIMSON_ANOMALY("Crimson Anomaly", 250, 10, 0.7, 3.2, "Strange red asteroids with exotic mineral signatures."),
    VOID_EDGE_BELT("Void Edge Belt", 300, 11, 0.8, 3.8, "At the edge of known space. High risk, high reward."),
    THE_MAW("The Maw", 400, 12, 0.9, 5.0, "A swirling debris field around something... hungry.");

    private final String displayName;
    private final int baseFuelCost;
    private final int baseTimeMinutes; // Scaled down significantly for gameplay (originally 30-120)
    private final double riskLevel;
    private final double rewardMultiplier;
    private final String description;

    ExpeditionDestination(String displayName, int baseFuelCost, int baseTimeMinutes, double riskLevel, double rewardMultiplier, String description) {
        this.displayName = displayName;
        this.baseFuelCost = baseFuelCost;
        this.baseTimeMinutes = baseTimeMinutes;
        this.riskLevel = riskLevel;
        this.rewardMultiplier = rewardMultiplier;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getBaseFuelCost() {
        return baseFuelCost;
    }

    public int getBaseTimeMinutes() {
        return baseTimeMinutes;
    }

    /**
     * Get the base journey time in milliseconds
     */
    public long getBaseTimeMs() {
        return baseTimeMinutes * 60L * 1000L;
    }

    /**
     * Get the base journey time in ticks (20 ticks = 1 second)
     */
    public int getBaseTimeTicks() {
        return baseTimeMinutes * 60 * 20;
    }

    public double getRiskLevel() {
        return riskLevel;
    }

    public double getRewardMultiplier() {
        return rewardMultiplier;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get risk level as a visual indicator (1-3 skulls)
     */
    public String getRiskIndicator() {
        if (riskLevel < 0.3) return "●○○";
        if (riskLevel < 0.6) return "●●○";
        return "●●●";
    }

    /**
     * Get a formatted display string for the destination
     */
    public String format() {
        return String.format("%s [%d fuel] %s", displayName, baseFuelCost, getRiskIndicator());
    }

    /**
     * Find a destination by name (case-insensitive)
     */
    public static ExpeditionDestination fromName(String name) {
        if (name == null) return null;
        String normalized = name.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        for (ExpeditionDestination dest : values()) {
            if (dest.name().equals(normalized) || 
                dest.displayName.equalsIgnoreCase(name.trim())) {
                return dest;
            }
        }
        return null;
    }
}

