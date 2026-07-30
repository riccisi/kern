package it.riccisi.kern.core.append;

import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.core.storage.EventStorage;
import it.riccisi.kern.core.storage.PreparedAppend;
import java.util.List;
import java.util.Objects;

final class CommittedAppend {
    private final EventStorage storage;
    private final PreparedAppend append;

    CommittedAppend(final EventStorage storage, final PreparedAppend append) {
        this.storage = Objects.requireNonNull(storage, "event storage must not be null");
        this.append = Objects.requireNonNull(append, "prepared append must not be null");
    }

    AppendResult result() {
        return storage.commit(List.of(append), append.request().durability()).results().getFirst();
    }
}
