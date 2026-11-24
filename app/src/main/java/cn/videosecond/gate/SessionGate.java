package cn.videosecond.gate;

/** Main-thread state machine. All timestamps are monotonic uptime in milliseconds. */
final class SessionGate {
    static final long INTERVAL_MS = 30 * 60 * 1000L;
    enum Presence { AWAY, TARGET, SYSTEM_PANEL }
    enum Stage { OUTSIDE, ENTRY_QUESTION, READING, RENEWAL_QUESTION }

    private Stage stage = Stage.OUTSIDE;
    private Presence presence = Presence.AWAY;
    private long questionId;
    private long remaining;
    private long lastObservation;

    void observe(Presence next, long now) {
        if (presence == Presence.TARGET && stage == Stage.READING) {
            remaining = Math.max(0, remaining - Math.max(0, now - lastObservation));
            if (remaining == 0) {
                ask(Stage.RENEWAL_QUESTION);
            }
        }
        presence = next;
        lastObservation = now;
        if (next == Presence.AWAY) {
            stage = Stage.OUTSIDE;
            remaining = 0;
        } else if (next == Presence.TARGET && stage == Stage.OUTSIDE) {
            ask(Stage.ENTRY_QUESTION);
        }
    }

    private void ask(Stage kind) {
        stage = kind;
        questionId++;
    }

    boolean grant(long submittedId, long now, boolean attached) {
        observe(presence, now);
        // Android can put our touchable quiz above a notification shade or
        // permission dialog. Honor that visible answer; SYSTEM_PANEL holds the clock.
        if (!attached || !hasQuestion() || presence == Presence.AWAY || questionId != submittedId) {
            return false;
        }
        stage = Stage.READING;
        remaining = INTERVAL_MS;
        return true;
    }

    boolean shouldShowQuestion() {
        return presence == Presence.TARGET && hasQuestion();
    }

    boolean hasQuestion() {
        return stage == Stage.ENTRY_QUESTION || stage == Stage.RENEWAL_QUESTION;
    }

    boolean isRenewal() { return stage == Stage.RENEWAL_QUESTION; }
    long questionId() { return questionId; }
    long remaining() { return remaining; }
    Presence presence() { return presence; }
    Stage stage() { return stage; }
}
