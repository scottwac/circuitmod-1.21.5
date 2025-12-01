package starduster.circuitmod.expedition;

/**
 * Status of an expedition through its lifecycle.
 */
public enum ExpeditionStatus {
    AWAITING_LAUNCH("Awaiting Launch"),
    IN_TRANSIT_OUTBOUND("In Transit (Outbound)"),
    AWAITING_DECISION("Awaiting Decision"),
    IN_TRANSIT_RETURN("In Transit (Return)"),
    COMPLETED_SUCCESS("Completed - Success"),
    COMPLETED_FAILURE("Completed - Failure"),
    ABORTED("Aborted");

    private final String displayName;

    ExpeditionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return this == IN_TRANSIT_OUTBOUND || this == AWAITING_DECISION || this == IN_TRANSIT_RETURN;
    }

    public boolean isCompleted() {
        return this == COMPLETED_SUCCESS || this == COMPLETED_FAILURE || this == ABORTED;
    }

    public boolean canClaim() {
        return this == COMPLETED_SUCCESS || this == COMPLETED_FAILURE;
    }
}

