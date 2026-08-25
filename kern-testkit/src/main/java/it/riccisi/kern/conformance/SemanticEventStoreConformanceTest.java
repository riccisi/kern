package it.riccisi.kern.conformance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import it.riccisi.kern.Attribute;
import it.riccisi.kern.Data;
import it.riccisi.kern.Event;
import it.riccisi.kern.EventFilter;
import it.riccisi.kern.EventId;
import it.riccisi.kern.EventStore;
import it.riccisi.kern.EventType;
import it.riccisi.kern.Metadata;
import it.riccisi.kern.Namespace;
import it.riccisi.kern.Position;
import it.riccisi.kern.StaleTailException;
import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.StoredEvents;
import it.riccisi.kern.TagName;
import it.riccisi.kern.Tags;
import it.riccisi.kern.filter.AnyEvents;
import it.riccisi.kern.filter.TaggedAs;
import it.riccisi.kern.filter.TypedBy;
import it.riccisi.kern.reduction.Excluding;
import it.riccisi.kern.reduction.Latest;
import it.riccisi.kern.reduction.LatestBy;
import it.riccisi.kern.reduction.Matching;
import it.riccisi.kern.tag.EventTag;
import it.riccisi.kern.tag.EventTags;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Reusable semantic conformance tests for {@link EventStore} implementations.
 *
 * <p>Subclasses must provide a fresh, empty store for each call to
 * {@link #store()}.</p>
 */
public abstract class SemanticEventStoreConformanceTest {

    /**
     * Creates a fresh event store for one conformance scenario.
     *
     * @return A fresh, empty event store.
     */
    protected abstract EventStore store();

    @Test
    public final void keepsObservationsBoundedAndRepeatablyIterable() {
        assertThat(
            "StoredEvents must be stable and repeatable instead of a cursor over the live log",
            this.repeatedIdsAfterLaterMatchingAppend(this.store()),
            contains(
                List.of(new EventId("course-created-7")),
                List.of(new EventId("course-created-7"))
            )
        );
    }

    @Test
    public final void rejectsRelevantAppendThroughStaleTail() {
        assertThat(
            "a tail must reject writes after a relevant event crosses its watermark",
            this.failureOf(() -> this.reuseTailAfterRelevantAppend(this.store())),
            is(equalTo(StaleTailException.class))
        );
    }

    @Test
    public final void keepsTailBoundaryBasedOnOriginalFilter() {
        assertThat(
            "a tail must ignore events outside the observation filter",
            this.idsAfterIrrelevantTailReuse(this.store()),
            contains(
                new EventId("student-enrolled-11"),
                new EventId("course-created-8")
            )
        );
    }

    @Test
    public final void rejectsNonPositiveSubscriptionDemand() {
        assertThat(
            "Subscription.next(count) must reject non-positive demand",
            this.failureOf(() -> this.store().events(
                new Namespace("academic-year-2026"),
                new TypedBy("CourseCreated")
            ).follow().next(0)),
            is(equalTo(IllegalArgumentException.class))
        );
    }

    @Test
    public final void returnsNonEmptyBoundedSubscriptionResults() {
        assertThat(
            "Subscription.next(count) must return a non-empty observation with at most count events",
            this.subscriptionResultSize(this.store(), 2),
            is(allOf(greaterThanOrEqualTo(1), lessThanOrEqualTo(2)))
        );
    }

    @Test
    public final void doesNotAdvanceSubscriptionWhenNextIsCalled() {
        assertThat(
            "repeated calls on the same Subscription must start from the same boundary",
            this.repeatedSubscriptionIds(this.store()),
            contains(
                List.of(new EventId("course-created-7")),
                List.of(new EventId("course-created-7"))
            )
        );
    }

    @Test
    public final void continuesSubscriptionFromReturnedObservation() {
        assertThat(
            "progress must be represented by following the returned StoredEvents",
            this.idsAfterFollowingFirstSubscriptionResult(this.store()),
            contains(new EventId("course-created-8"))
        );
    }

    @Test
    public final void waitsForFutureSubscriptionEvents() {
        assertThat(
            "Subscription.next(count) must remain pending until a matching event exists",
            this.pendingBeforeMatchingAppend(this.store()),
            is(equalTo(false))
        );
    }

    @Test
    public final void keepsLatestEventOnly() {
        assertThat(
            "Latest must keep only the greatest Position in the bounded observation",
            this.idsAfterLatestReduction(this.store()),
            contains(new EventId("course-changed-7"))
        );
    }

    @Test
    public final void keepsLatestEventByTagValue() {
        assertThat(
            "LatestBy must keep the latest event for each distinct tag value in Position order",
            this.idsAfterLatestByReduction(this.store()),
            contains(
                new EventId("course-created-8"),
                new EventId("course-changed-7")
            )
        );
    }

    @Test
    public final void keepsOnlyMatchingReducedEvents() {
        assertThat(
            "Matching must reduce the representation without changing the source observation",
            this.idsAfterMatchingReduction(this.store()),
            contains(
                new EventId("course-created-7"),
                new EventId("course-created-8")
            )
        );
    }

    @Test
    public final void excludesMatchingReducedEvents() {
        assertThat(
            "Excluding must remove matching events from the current representation",
            this.idsAfterExcludingReduction(this.store()),
            contains(
                new EventId("course-created-7"),
                new EventId("course-created-8"),
                new EventId("course-changed-7")
            )
        );
    }

    @Test
    public final void returnsEmptyLatestReductionForEmptyObservation() {
        assertThat(
            "Latest over an empty bounded observation must remain empty",
            this.idsAfterEmptyLatestReduction(this.store()),
            is(emptyIterable())
        );
    }

    @Test
    public final void preservesSurvivingOrderAfterReduction() {
        assertThat(
            "reductions may discard events but must not reorder survivors",
            this.positionsAfterLatestByReduction(this.store()),
            contains(new Position(3L), new Position(4L))
        );
    }

    @Test
    public final void appliesReductionPipelineInDeclarationOrder() {
        assertThat(
            "reduction composition order is part of the semantic contract",
            this.idsFromOrderedReductionPipelines(this.store()),
            contains(
                List.of(new EventId("course-created-7"), new EventId("course-created-8")),
                List.of(new EventId("course-created-8"))
            )
        );
    }

    @Test
    public final void doesNotTraverseObservationWhenReductionIsDeclared() {
        assertThat(
            "reduce() must create a lazy derived observation without traversing the source",
            this.typeCallsAfterDeclaringReduction(this.store()),
            is(equalTo(0))
        );
    }

    @Test
    public final void preservesTailSemanticsAfterReduction() {
        assertThat(
            "a reduced observation tail must keep the original dependency boundary",
            this.failureOf(() -> this.reuseReducedTailAfterOriginalDependencyConflict(this.store())),
            is(equalTo(StaleTailException.class))
        );
    }

    private Iterable<List<EventId>> repeatedIdsAfterLaterMatchingAppend(final EventStore store) {
        final Namespace namespace = new Namespace("bounded-observation");
        this.append(
            store,
            namespace,
            new SampleEvent("course-created-7", "CourseCreated", "courseId", "c7")
        );
        final StoredEvents history = store.events(namespace, new TypedBy("CourseCreated"));
        this.append(
            store,
            namespace,
            new SampleEvent("course-created-8", "CourseCreated", "courseId", "c8")
        );
        return List.of(this.ids(history), this.ids(history));
    }

    private void reuseTailAfterRelevantAppend(final EventStore store) {
        final Namespace namespace = new Namespace("stale-tail");
        final StoredEvents history = store.events(namespace, new TypedBy("CourseCreated"));
        history.tail().append(new SampleEvent("course-created-7", "CourseCreated", "courseId", "c7"));
        history.tail().append(new SampleEvent("course-created-8", "CourseCreated", "courseId", "c8"));
    }

    private Iterable<EventId> idsAfterIrrelevantTailReuse(final EventStore store) {
        final Namespace namespace = new Namespace("tail-boundary");
        final StoredEvents history = store.events(namespace, new TypedBy("CourseCreated"));
        history.tail().append(new SampleEvent("student-enrolled-11", "StudentEnrolled", "studentId", "s11"));
        history.tail().append(new SampleEvent("course-created-8", "CourseCreated", "courseId", "c8"));
        return this.ids(store.events(namespace, this.sampleEvents()));
    }

    private int subscriptionResultSize(final EventStore store, final int demand) {
        final Namespace namespace = new Namespace("subscription-size");
        final CompletionStage<StoredEvents> next = store.events(
            namespace,
            new TypedBy("CourseCreated")
        ).follow().next(demand);
        this.append(
            store,
            namespace,
            new SampleEvent("course-created-7", "CourseCreated", "courseId", "c7"),
            new SampleEvent("course-created-8", "CourseCreated", "courseId", "c8"),
            new SampleEvent("course-created-9", "CourseCreated", "courseId", "c9")
        );
        return this.ids(this.completed(next)).size();
    }

    private Iterable<List<EventId>> repeatedSubscriptionIds(final EventStore store) {
        final Namespace namespace = new Namespace("subscription-repeat");
        final var subscription = store.events(namespace, new TypedBy("CourseCreated")).follow();
        this.append(
            store,
            namespace,
            new SampleEvent("course-created-7", "CourseCreated", "courseId", "c7"),
            new SampleEvent("course-created-8", "CourseCreated", "courseId", "c8")
        );
        return List.of(
            this.ids(this.completed(subscription.next(1))),
            this.ids(this.completed(subscription.next(1)))
        );
    }

    private Iterable<EventId> idsAfterFollowingFirstSubscriptionResult(final EventStore store) {
        final Namespace namespace = new Namespace("subscription-continuation");
        final var subscription = store.events(namespace, new TypedBy("CourseCreated")).follow();
        this.append(
            store,
            namespace,
            new SampleEvent("course-created-7", "CourseCreated", "courseId", "c7"),
            new SampleEvent("course-created-8", "CourseCreated", "courseId", "c8")
        );
        return this.ids(this.completed(this.completed(subscription.next(1)).follow().next(10)));
    }

    private boolean pendingBeforeMatchingAppend(final EventStore store) {
        return store.events(
            new Namespace("subscription-waiting"),
            new TypedBy("CourseCreated")
        ).follow().next(1).toCompletableFuture().isDone();
    }

    private Iterable<EventId> idsAfterLatestReduction(final EventStore store) {
        return this.ids(this.history(store, new Namespace("latest")).reduce(new Latest()));
    }

    private Iterable<EventId> idsAfterLatestByReduction(final EventStore store) {
        return this.ids(
            this.history(store, new Namespace("latest-by")).reduce(new LatestBy(new TagName("courseId")))
        );
    }

    private Iterable<EventId> idsAfterMatchingReduction(final EventStore store) {
        return this.ids(
            this.history(store, new Namespace("matching")).reduce(new Matching(new TypedBy("CourseCreated")))
        );
    }

    private Iterable<EventId> idsAfterExcludingReduction(final EventStore store) {
        return this.ids(
            this.history(store, new Namespace("excluding")).reduce(new Excluding(new TypedBy("CourseRemoved")))
        );
    }

    private Iterable<EventId> idsAfterEmptyLatestReduction(final EventStore store) {
        return this.ids(store.events(
            new Namespace("empty-latest"),
            new TypedBy("CourseCreated")
        ).reduce(new Latest()));
    }

    private Iterable<Position> positionsAfterLatestByReduction(final EventStore store) {
        return this.positions(
            this.history(store, new Namespace("latest-by-order")).reduce(new LatestBy(new TagName("courseId")))
        );
    }

    private Iterable<List<EventId>> idsFromOrderedReductionPipelines(final EventStore store) {
        final StoredEvents history = this.history(store, new Namespace("ordered-reductions"));
        return List.of(
            this.ids(history.reduce(new Matching(new TypedBy("CourseCreated"))).reduce(new LatestBy(new TagName("courseId")))),
            this.ids(history.reduce(new LatestBy(new TagName("courseId"))).reduce(new Matching(new TypedBy("CourseCreated"))))
        );
    }

    private int typeCallsAfterDeclaringReduction(final EventStore store) {
        final Namespace namespace = new Namespace("lazy-reduction");
        final CountingEvent event = new CountingEvent("course-created-7", "CourseCreated", "courseId", "c7");
        this.append(store, namespace, event);
        event.reset();
        store.events(namespace, new TypedBy("CourseCreated")).reduce(new Matching(new TypedBy("CourseCreated")));
        return event.calls();
    }

    private void reuseReducedTailAfterOriginalDependencyConflict(final EventStore store) {
        final Namespace namespace = new Namespace("reduced-tail");
        final StoredEvents history = this.history(store, namespace);
        final StoredEvents reduced = history.reduce(new Matching(new TypedBy("CourseCreated")));
        this.append(store, namespace, new SampleEvent("course-removed-8", "CourseRemoved", "courseId", "c8"));
        reduced.tail().append(new SampleEvent("course-created-9", "CourseCreated", "courseId", "c9"));
    }

    private StoredEvents history(final EventStore store, final Namespace namespace) {
        this.append(
            store,
            namespace,
            new SampleEvent("course-created-7", "CourseCreated", "courseId", "c7"),
            new SampleEvent("course-removed-7", "CourseRemoved", "courseId", "c7"),
            new SampleEvent("course-created-8", "CourseCreated", "courseId", "c8"),
            new SampleEvent("course-changed-7", "CourseChanged", "courseId", "c7")
        );
        return store.events(namespace, this.sampleEvents());
    }

    private void append(final EventStore store, final Namespace namespace, final Event... events) {
        store.events(namespace, this.sampleEvents()).tail().append(events);
    }

    private EventFilter sampleEvents() {
        return new AnyEvents(
            new TypedBy("CourseCreated"),
            new TypedBy("CourseRemoved"),
            new TypedBy("CourseChanged"),
            new TypedBy("StudentEnrolled")
        );
    }

    private List<EventId> ids(final StoredEvents events) {
        final List<EventId> ids = new ArrayList<>();
        for (final StoredEvent event : events) {
            ids.add(event.id());
        }
        return ids;
    }

    private List<Position> positions(final StoredEvents events) {
        final List<Position> positions = new ArrayList<>();
        for (final StoredEvent event : events) {
            positions.add(event.position());
        }
        return positions;
    }

    private StoredEvents completed(final CompletionStage<StoredEvents> stage) {
        try {
            return stage.toCompletableFuture().get(
                Duration.ofSeconds(1L).toMillis(),
                TimeUnit.MILLISECONDS
            );
        } catch (final Exception failure) {
            throw new IllegalStateException("CompletionStage did not complete in time", failure);
        }
    }

    private Class<? extends Throwable> failureOf(final Action action) {
        Class<? extends Throwable> failure = null;
        try {
            action.run();
        } catch (final Throwable thrown) {
            failure = thrown.getClass();
        }
        return failure;
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private record SampleEvent(EventId id, EventType type, Tags tags) implements Event {

        SampleEvent(
            final String id,
            final String type,
            final String tag,
            final String value
        ) {
            this(new EventId(id), new EventType(type), new EventTags(new EventTag(tag, value)));
        }

        @Override
        public Data data() {
            return EmptyData.INSTANCE;
        }
    }

    private static final class CountingEvent implements Event {

        private final SampleEvent origin;
        private int calls;

        CountingEvent(
            final String id,
            final String type,
            final String tag,
            final String value
        ) {
            this.origin = new SampleEvent(id, type, tag, value);
        }

        @Override
        public EventId id() {
            return this.origin.id();
        }

        @Override
        public EventType type() {
            this.calls += 1;
            return this.origin.type();
        }

        @Override
        public Tags tags() {
            return this.origin.tags();
        }

        @Override
        public Data data() {
            return this.origin.data();
        }

        void reset() {
            this.calls = 0;
        }

        int calls() {
            return this.calls;
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
        public java.util.Iterator<Attribute<?>> iterator() {
            return List.<Attribute<?>>of().iterator();
        }
    }
}
