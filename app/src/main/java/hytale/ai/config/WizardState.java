package hytale.ai.config;

public class WizardState {

    private final String pendingName;
    private final long createdAt;
    private static final long TIMEOUT_MS = 5 * 60 * 1000L;

    public WizardState(String pendingName) {
        this.pendingName = pendingName;
        this.createdAt = System.currentTimeMillis();
    }

    public String getPendingName() { return pendingName; }

    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt > TIMEOUT_MS;
    }
}
