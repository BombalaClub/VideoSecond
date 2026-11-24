package cn.videosecond.gate;

import static cn.videosecond.gate.SessionGate.Presence.*;
import static cn.videosecond.gate.SessionGate.Stage.*;

public final class SessionGateCheck {
    private static void check(boolean pass, String reason) {
        if (!pass) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        SessionGate gate = new SessionGate();
        gate.observe(SYSTEM_PANEL, 0);
        check(!gate.shouldShowQuestion(), "a system panel at startup needs no question");
        gate.observe(TARGET, 10);
        long entry = gate.questionId();
        gate.observe(TARGET, 100);
        check(gate.shouldShowQuestion() && !gate.isRenewal() && gate.questionId() == entry,
                "in-app transitions retain entry question");
        gate.observe(SYSTEM_PANEL, 110);
        check(!gate.grant(entry, 111, false), "a detached question cannot grant access");
        gate.observe(TARGET, 120);
        check(gate.grant(entry, 121, true), "correct visible entry grants time");
        check(!gate.grant(entry, 122, true), "old tap cannot reset timer");
        gate.observe(SYSTEM_PANEL, 1121);
        long balance = SessionGate.INTERVAL_MS - 1000;
        check(gate.remaining() == balance, "one second of browsing");
        gate.observe(TARGET, 601121);
        check(gate.remaining() == balance, "time spent under notification shade excluded");
        long due = 601121 + balance;
        gate.observe(TARGET, due - 1);
        check(!gate.shouldShowQuestion() && gate.remaining() == 1, "just before deadline");
        gate.observe(TARGET, due);
        long renewal = gate.questionId();
        check(gate.shouldShowQuestion() && gate.isRenewal() && renewal != entry,
                "exactly 30 minutes of active reading requires renewal");
        check(!gate.grant(entry, due + 1, true), "stale question cannot renew");
        check(gate.grant(renewal, due + 2, true), "correct renewal starts next period");
        gate.observe(TARGET, due + 2 + SessionGate.INTERVAL_MS);
        check(gate.shouldShowQuestion() && gate.isRenewal(), "renewals repeat");
        gate.observe(AWAY, due + 3 + SessionGate.INTERVAL_MS);
        check(gate.stage() == OUTSIDE && !gate.shouldShowQuestion(), "home clears grant");
        gate.observe(TARGET, due + 4 + SessionGate.INTERVAL_MS);
        check(!gate.isRenewal() && gate.shouldShowQuestion(), "return requires a fresh entry");
        long freshEntry = gate.questionId();
        gate.observe(AWAY, due + 5 + SessionGate.INTERVAL_MS);
        gate.observe(TARGET, due + 6 + SessionGate.INTERVAL_MS);
        check(!gate.grant(freshEntry, due + 7 + SessionGate.INTERVAL_MS, true),
                "return from lock/home rejects previous entry");
        check(new SessionGate().stage() == OUTSIDE, "service recreation cannot reuse a grant");
        SessionGate covered = new SessionGate();
        covered.observe(TARGET, 10);
        long initialQuestion = covered.questionId();
        covered.observe(SYSTEM_PANEL, 20);
        check(!covered.grant(initialQuestion + 1, 21, true), "panel rejects a stale attached view");
        check(!covered.grant(initialQuestion, 22, false), "panel rejects a detached view");
        check(covered.grant(initialQuestion, 23, true), "panel accepts its still-visible correct answer");
        long panelEnd = 3 * SessionGate.INTERVAL_MS;
        covered.observe(SYSTEM_PANEL, panelEnd);
        check(covered.stage() == READING && covered.remaining() == SessionGate.INTERVAL_MS,
                "time before the notification/permission panel closes is free");
        covered.observe(TARGET, panelEnd + 1);
        covered.observe(TARGET, panelEnd + SessionGate.INTERVAL_MS);
        check(covered.remaining() == 1, "the new 30-minute period started only on return");
        covered.observe(TARGET, panelEnd + SessionGate.INTERVAL_MS + 1);
        check(covered.isRenewal() && covered.shouldShowQuestion(), "timer expires after return");
        long stale = covered.questionId();
        covered.observe(AWAY, panelEnd + SessionGate.INTERVAL_MS + 2);
        check(!covered.grant(stale, panelEnd + SessionGate.INTERVAL_MS + 3, true),
                "switching away invalidates a pending correct callback");
        System.out.println("SessionGateCheck passed");
    }
}
