package it.riccisi.kern.api.value;

public record SubjectRevision(long value) {
    public SubjectRevision {
        if (value < 0) {
            throw new IllegalArgumentException("subject revision must not be negative");
        }
    }

    public SubjectRevision next() {
        if (value == Long.MAX_VALUE) {
            throw new IllegalStateException("subject revision cannot advance beyond Long.MAX_VALUE");
        }
        return new SubjectRevision(value + 1);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
