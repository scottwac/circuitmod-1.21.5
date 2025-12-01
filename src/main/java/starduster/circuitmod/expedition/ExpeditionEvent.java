package starduster.circuitmod.expedition;

import net.minecraft.nbt.NbtCompound;

import java.util.Random;

/**
 * Represents a random event that occurs during an expedition requiring player decision.
 */
public class ExpeditionEvent {
    private final EventType type;
    private final String title;
    private final String description;
    private final EventChoice choiceA; // Typically "safe" option
    private final EventChoice choiceB; // Typically "risky" option
    private final long triggeredAt;

    public ExpeditionEvent(EventType type, String title, String description, 
                          EventChoice choiceA, EventChoice choiceB, long triggeredAt) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.choiceA = choiceA;
        this.choiceB = choiceB;
        this.triggeredAt = triggeredAt;
    }

    public EventType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public EventChoice getChoiceA() {
        return choiceA;
    }

    public EventChoice getChoiceB() {
        return choiceB;
    }

    public long getTriggeredAt() {
        return triggeredAt;
    }

    public NbtCompound writeNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Type", type.name());
        nbt.putString("Title", title);
        nbt.putString("Description", description);
        nbt.put("ChoiceA", choiceA.writeNbt());
        nbt.put("ChoiceB", choiceB.writeNbt());
        nbt.putLong("TriggeredAt", triggeredAt);
        return nbt;
    }

    public static ExpeditionEvent fromNbt(NbtCompound nbt) {
        return new ExpeditionEvent(
            EventType.valueOf(nbt.getString("Type").orElse("ASTEROID_COLLISION")),
            nbt.getString("Title").orElse("Unknown Event"),
            nbt.getString("Description").orElse(""),
            EventChoice.fromNbt(nbt.getCompoundOrEmpty("ChoiceA")),
            EventChoice.fromNbt(nbt.getCompoundOrEmpty("ChoiceB")),
            nbt.getLong("TriggeredAt").orElse(System.currentTimeMillis())
        );
    }

    /**
     * Generate a random event based on the expedition's destination danger level.
     */
    public static ExpeditionEvent generateRandomEvent(ExpeditionDestination destination, Random random) {
        double dangerLevel = destination.getRiskLevel();
        
        // Weight event types by danger level
        EventType[] types = EventType.values();
        EventType selectedType = types[random.nextInt(types.length)];
        
        // Higher danger = more likely to get threatening events
        if (dangerLevel > 0.5 && random.nextDouble() < dangerLevel) {
            // Bias toward dangerous events
            EventType[] dangerousTypes = {
                EventType.ASTEROID_COLLISION,
                EventType.PIRATE_ENCOUNTER,
                EventType.RADIATION_STORM,
                EventType.HULL_BREACH
            };
            selectedType = dangerousTypes[random.nextInt(dangerousTypes.length)];
        } else if (dangerLevel < 0.3 && random.nextDouble() > dangerLevel) {
            // Bias toward positive/neutral events
            EventType[] safeTypes = {
                EventType.RICH_DEPOSIT_FOUND,
                EventType.MYSTERIOUS_SIGNAL,
                EventType.EQUIPMENT_MALFUNCTION
            };
            selectedType = safeTypes[random.nextInt(safeTypes.length)];
        }

        return createEventForType(selectedType, System.currentTimeMillis());
    }

    private static ExpeditionEvent createEventForType(EventType type, long triggeredAt) {
        return switch (type) {
            case ASTEROID_COLLISION -> new ExpeditionEvent(
                type,
                "Collision Warning!",
                "A rogue asteroid is on an intercept course! Your navigator can attempt evasive maneuvers, but it will cost time and fuel.",
                new EventChoice(
                    "Take evasive action",
                    "You veer off course, avoiding the collision safely.",
                    0.1, 0.9, 0.0, 0.0
                ),
                new EventChoice(
                    "Brace for impact and push through",
                    "The ship rocks violently but holds together... barely.",
                    -0.15, 1.1, 0.2, 0.1
                ),
                triggeredAt
            );
            
            case EQUIPMENT_MALFUNCTION -> new ExpeditionEvent(
                type,
                "Equipment Failure",
                "The mining drill has overheated. You can wait for it to cool down or push it beyond recommended limits.",
                new EventChoice(
                    "Wait for cooldown",
                    "The drill cools down safely. Operations resume.",
                    0.05, 0.85, 0.0, 0.0
                ),
                new EventChoice(
                    "Push the equipment harder",
                    "Sparks fly but the drill keeps spinning!",
                    -0.1, 1.25, 0.15, 0.2
                ),
                triggeredAt
            );
            
            case MYSTERIOUS_SIGNAL -> new ExpeditionEvent(
                type,
                "Unknown Signal Detected",
                "Your comms array has picked up a strange repeating signal from a nearby asteroid. It could be a distress beacon... or a trap.",
                new EventChoice(
                    "Ignore it and stay on mission",
                    "You continue on your planned route.",
                    0.05, 1.0, 0.0, 0.0
                ),
                new EventChoice(
                    "Investigate the signal",
                    "You alter course toward the source...",
                    -0.1, 1.0, 0.25, 0.4
                ),
                triggeredAt
            );
            
            case PIRATE_ENCOUNTER -> new ExpeditionEvent(
                type,
                "Pirates Inbound!",
                "A hostile vessel is approaching fast. They're demanding you drop your cargo or face destruction.",
                new EventChoice(
                    "Jettison some cargo and flee",
                    "You dump part of your haul and escape to safety.",
                    0.2, 0.5, 0.0, 0.0
                ),
                new EventChoice(
                    "Full speed ahead - try to outrun them",
                    "You push the engines to maximum!",
                    -0.1, 1.0, 0.35, 0.05
                ),
                triggeredAt
            );
            
            case RADIATION_STORM -> new ExpeditionEvent(
                type,
                "Radiation Storm Approaching",
                "Sensors detect a wave of intense radiation sweeping through the area. You can shelter behind an asteroid or try to mine through it with shields up.",
                new EventChoice(
                    "Take shelter and wait it out",
                    "You hunker down until the storm passes.",
                    0.1, 0.8, 0.0, 0.0
                ),
                new EventChoice(
                    "Shields up, keep mining",
                    "The shields strain but hold. You keep working.",
                    -0.2, 1.15, 0.2, 0.15
                ),
                triggeredAt
            );
            
            case RICH_DEPOSIT_FOUND -> new ExpeditionEvent(
                type,
                "Rich Deposit Discovered!",
                "Your scanners found an incredibly dense mineral vein, but it's in an unstable cavern. Mining it could cause a collapse.",
                new EventChoice(
                    "Take what's safe to extract",
                    "You carefully extract surface minerals.",
                    0.05, 1.2, 0.0, 0.1
                ),
                new EventChoice(
                    "Go deep - maximum extraction",
                    "You drill into the heart of the deposit...",
                    -0.15, 1.8, 0.3, 0.5
                ),
                triggeredAt
            );
            
            case CREW_ILLNESS -> new ExpeditionEvent(
                type,
                "Crew Member Down",
                "One of your crew has fallen ill from an unknown pathogen. You can abort the mission to get them help or continue short-handed.",
                new EventChoice(
                    "Abort and return home early",
                    "You turn back. The crew member will recover.",
                    0.3, 0.4, 0.0, 0.0
                ),
                new EventChoice(
                    "Continue the mission",
                    "You press on with reduced efficiency.",
                    -0.2, 0.9, 0.1, 0.0
                ),
                triggeredAt
            );
            
            case NAVIGATION_ERROR -> new ExpeditionEvent(
                type,
                "Navigation Systems Glitching",
                "Your nav computer is showing conflicting data. You can recalibrate (losing time) or trust your pilot's instincts.",
                new EventChoice(
                    "Full system recalibration",
                    "The systems come back online correctly.",
                    0.1, 0.85, 0.0, 0.0
                ),
                new EventChoice(
                    "Trust the pilot",
                    "Your pilot takes manual control...",
                    0.0, 1.0, 0.15, 0.2
                ),
                triggeredAt
            );
            
            case ALIEN_ARTIFACT -> new ExpeditionEvent(
                type,
                "Strange Object Detected",
                "Your crew has found an object of clearly artificial origin, but it's not human-made. It pulses with an eerie energy.",
                new EventChoice(
                    "Log the coordinates and leave it",
                    "You mark the location for future study and move on.",
                    0.1, 1.0, 0.0, 0.05
                ),
                new EventChoice(
                    "Bring it aboard",
                    "Against all protocols, you load the artifact...",
                    -0.25, 1.0, 0.2, 0.6
                ),
                triggeredAt
            );
            
            case HULL_BREACH -> new ExpeditionEvent(
                type,
                "Hull Breach!",
                "Micrometeorites have punctured the cargo hold! You can emergency weld it now or continue and hope it holds.",
                new EventChoice(
                    "Stop and repair immediately",
                    "Your crew patches the breach. Crisis averted.",
                    0.15, 0.9, 0.0, 0.0
                ),
                new EventChoice(
                    "Seal the section and continue",
                    "You lock down the damaged section and press on.",
                    -0.2, 1.05, 0.25, 0.0
                ),
                triggeredAt
            );
        };
    }
}

