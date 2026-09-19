package com.kroxaboom.skazka.data.sync;

import java.util.Objects;

/**
 * RU: Трёхстороннее сравнение прогресса. Отсутствующий baseline никогда не разрешает
 * перезаписать уже существующий remote progress.
 *
 * EN: Three-way progress comparison. A missing baseline never authorizes overwriting
 * existing remote progress.
 */
public final class SyncPolicy {
    private SyncPolicy() {}

    public static SyncAction decide(
            boolean dirty,
            boolean baselineKnown,
            String baseline,
            String local,
            String remote
    ) {
        if (!dirty) {
            return SyncAction.ACCEPT_REMOTE;
        }
        if (Objects.equals(local, remote)) {
            return SyncAction.ACKNOWLEDGE;
        }
        if (baselineKnown && Objects.equals(baseline, remote)) {
            return SyncAction.SEND_LOCAL;
        }
        if (!baselineKnown && isEmpty(remote)) {
            return SyncAction.SEND_LOCAL;
        }
        return SyncAction.CONFLICT;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
