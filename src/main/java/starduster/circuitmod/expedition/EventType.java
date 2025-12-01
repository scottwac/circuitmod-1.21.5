package starduster.circuitmod.expedition;

/**
 * Types of random events that can occur during expeditions.
 */
public enum EventType {
    ASTEROID_COLLISION("Collision Warning!"),
    EQUIPMENT_MALFUNCTION("Equipment Failure"),
    MYSTERIOUS_SIGNAL("Unknown Signal Detected"),
    PIRATE_ENCOUNTER("Pirates Inbound!"),
    RADIATION_STORM("Radiation Storm Approaching"),
    RICH_DEPOSIT_FOUND("Rich Deposit Discovered!"),
    CREW_ILLNESS("Crew Member Down"),
    NAVIGATION_ERROR("Navigation Systems Glitching"),
    ALIEN_ARTIFACT("Strange Object Detected"),
    HULL_BREACH("Hull Breach!");

    private final String title;

    EventType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}

