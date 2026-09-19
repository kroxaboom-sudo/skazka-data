import com.kroxaboom.skazka.data.sync.SyncAction;
import com.kroxaboom.skazka.data.sync.SyncPlan;
import com.kroxaboom.skazka.data.sync.SyncPolicy;
import com.kroxaboom.skazka.data.sync.SyncRecord;

import java.util.List;

public final class SyncCoreSelfTest {
    public static void main(String[] args) {
        check(
                SyncPolicy.decide(false, true, "old", "new", "remote")
                        == SyncAction.ACCEPT_REMOTE,
                "clean state accepts remote"
        );
        check(
                SyncPolicy.decide(true, true, "old", "same", "same")
                        == SyncAction.ACKNOWLEDGE,
                "matching local/remote is acknowledged"
        );
        check(
                SyncPolicy.decide(true, true, "base", "local", "base")
                        == SyncAction.SEND_LOCAL,
                "known unchanged baseline allows local write"
        );
        check(
                SyncPolicy.decide(true, false, "", "local", "")
                        == SyncAction.SEND_LOCAL,
                "missing baseline can write only when remote is empty"
        );
        check(
                SyncPolicy.decide(true, false, "", "local", "remote")
                        == SyncAction.CONFLICT,
                "missing baseline protects existing remote progress"
        );

        long now = 1_700_000_000_000L;
        List<SyncRecord> records = List.of(
                new SyncRecord("fresh", false, false, "", 0, now - 1_000),
                new SyncRecord("dirty-old", true, false, "", 0, now - 20_000),
                new SyncRecord("dirty-new", true, false, "", 0, now - 10_000),
                new SyncRecord("stale", false, false, "", 0, now - SyncPlan.DEFAULT_FRESH_MS),
                new SyncRecord("unchecked", false, false, "", 0, 0),
                new SyncRecord("error", false, false, "offline", 0, now - 1_000),
                new SyncRecord("conflict", true, true, "", 0, 0),
                new SyncRecord("retry-later", true, false, "", now + 1, 0)
        );

        List<SyncRecord> due = SyncPlan.due(records, now);
        check(due.size() == 5, "due record count");
        check("dirty-old".equals(due.get(0).id()), "dirty records are first and oldest first");
        check("dirty-new".equals(due.get(1).id()), "second dirty record");
        check(due.stream().noneMatch(item -> item.id().equals("fresh")), "fresh clean record skipped");
        check(due.stream().noneMatch(item -> item.id().equals("conflict")), "conflict skipped");
        check(due.stream().noneMatch(item -> item.id().equals("retry-later")), "future retry skipped");

        System.out.println("PASS: Skazka Sync Core conflict and queue policy");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
