package it.riccisi.kern.memory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import it.riccisi.kern.Attribute;
import it.riccisi.kern.Data;
import it.riccisi.kern.Event;
import it.riccisi.kern.EventId;
import it.riccisi.kern.EventType;
import it.riccisi.kern.Metadata;
import it.riccisi.kern.NamespaceId;
import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.Tags;
import it.riccisi.kern.filter.TypedBy;
import it.riccisi.kern.tag.EventTags;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MemoryEventStoreTest {

    @Test
    void acceptsExactIdempotentRetryBeforeTailValidation() {
        assertThat(
            "exact retry must succeed even when the original tail is stale",
            MemoryEventStoreTest.idsAfterExactRetry(),
            contains(new EventId("course-created-7"))
        );
    }

    @Test
    void rejectsSameEventIdWithDifferentFacts() {
        assertThat(
            "same EventId with different semantic facts must be rejected",
            MemoryEventStoreTest.failureOf(MemoryEventStoreTest::appendConflictingIdentity),
            is(instanceOf(IllegalArgumentException.class))
        );
    }

    @Test
    void rejectsPartialDuplicateBatch() {
        assertThat(
            "append must not mix already stored EventIds with new EventIds",
            MemoryEventStoreTest.failureOf(MemoryEventStoreTest::appendPartialDuplicateBatch),
            is(instanceOf(IllegalArgumentException.class))
        );
    }

    @Test
    void rejectsDuplicateIdsInsideBatch() {
        assertThat(
            "append batch must not contain duplicate EventIds",
            MemoryEventStoreTest.failureOf(MemoryEventStoreTest::appendDuplicateIdsInsideBatch),
            is(instanceOf(IllegalArgumentException.class))
        );
    }

    private static List<EventId> idsAfterExactRetry() {
        final MemoryEventStore store = new MemoryEventStore();
        final var history = store.events(
            new NamespaceId("idempotent-retry"),
            new TypedBy("CourseCreated")
        );
        final Event event = new SampleEvent("course-created-7", "CourseCreated");
        history.tail().append(event);
        history.tail().append(event);
        final List<EventId> ids = new ArrayList<>();
        for (
            final StoredEvent stored
                : store.events(new NamespaceId("idempotent-retry"), new TypedBy("CourseCreated"))
        ) {
            ids.add(stored.id());
        }
        return ids;
    }

    private static void appendConflictingIdentity() {
        final MemoryEventStore store = new MemoryEventStore();
        final var tail = store.events(
            new NamespaceId("conflicting-identity"),
            new TypedBy("CourseCreated")
        ).tail();
        tail.append(new SampleEvent("course-created-7", "CourseCreated"));
        tail.append(new SampleEvent("course-created-7", "CourseChanged"));
    }

    private static void appendPartialDuplicateBatch() {
        final MemoryEventStore store = new MemoryEventStore();
        final var tail = store.events(
            new NamespaceId("partial-duplicate"),
            new TypedBy("CourseCreated")
        ).tail();
        tail.append(new SampleEvent("course-created-7", "CourseCreated"));
        tail.append(
            new SampleEvent("course-created-7", "CourseCreated"),
            new SampleEvent("course-created-8", "CourseCreated")
        );
    }

    private static void appendDuplicateIdsInsideBatch() {
        final MemoryEventStore store = new MemoryEventStore();
        store.events(
            new NamespaceId("duplicate-in-batch"),
            new TypedBy("CourseCreated")
        ).tail().append(
            new SampleEvent("course-created-7", "CourseCreated"),
            new SampleEvent("course-created-7", "CourseCreated")
        );
    }

    private static Throwable failureOf(final Action action) {
        Throwable failure = null;
        try {
            action.run();
        } catch (final Throwable thrown) {
            failure = thrown;
        }
        return failure;
    }

    @FunctionalInterface
    private interface Action {
        void run();
    }

    private record SampleEvent(EventId id, EventType type, Tags tags) implements Event {

        SampleEvent(final String id, final String type) {
            this(new EventId(id), new EventType(type), new EventTags());
        }

        @Override
        public Data data() {
            return EmptyData.INSTANCE;
        }
    }

    private enum EmptyData implements Data {
        INSTANCE;

        @Override
        public Metadata meta() {
            return new EmptyMetadata();
        }

        @Override
        public <T> T value(final Attribute<T> attribute) {
            throw new IllegalArgumentException("Attribute is not part of empty data");
        }
    }

    private static final class EmptyMetadata implements Metadata {

        @Override
        public String name() {
            return "empty";
        }

        @Override
        public Iterator<Attribute<?>> iterator() {
            return List.<Attribute<?>>of().iterator();
        }
    }
}
