package it.riccisi.kern.core.append;

import java.util.concurrent.atomic.AtomicLong;

public final class SequentialDiagnosticIds implements DiagnosticIds {
    private final AtomicLong sequence;

    public SequentialDiagnosticIds() {
        this.sequence = new AtomicLong();
    }

    @Override
    public String next() {
        return "append-" + sequence.incrementAndGet();
    }
}
