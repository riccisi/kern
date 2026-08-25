package it.riccisi.kern.conformance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
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
import it.riccisi.kern.filter.AllEvents;
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
import java.util.stream.StreamSupport;
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
    public final void matchesEventsByType() {
        assertThat(
            "TypedBy must select only events with the requested EventType",
            this.idsMatching(this.store(), new TypedBy("CourseCreated")),
            contains(
                new EventId("course-created-7"),
                new EventId("course-created-8")
            )
        );
    }

    @Test
    public final void matchesEventsByTag() {
        assertThat(
            "TaggedAs must select only events carrying the requested tag",
            this.idsMatching(this.store(), new TaggedAs("studentId", "s11")),
            contains(new EventId("student-enrolled-11"))
        );
    }

    @Test
    public final void intersectsFiltersWithAllEvents() {
        assertThat(
            "AllEvents must select only events matching every child filter",
            this.idsMatching(
                this.store(),
                new AllEvents(
                    new TypedBy("CourseCreated"),
                    new TaggedAs("courseId", "c7")
                )
            ),
            contains(new EventId("course-created-7"))
        );
    }

    @Test
    public final void unitesFiltersWithAnyEvents() {
        assertThat(
            "AnyEvents must select events matching at least one child filter",
            this.idsMatching(
                this.store(),
                new AnyEvents(
                    new TypedBy("CourseRemoved"),
                    new TaggedAs("studentId", "s11")
                )
            ),
            contains(
                new EventId("course-removed-7"),
                new EventId("student-enrolled-11")
            )
        );
    }

    @Test
    public final void composesNestedFilters() {
        assertThat(
            "nested filters must preserve AND/OR algebra semantics",
            this.idsMatching(
                this.store(),
                new AllEvents(
                    new AnyEvents(
                        new TypedBy("CourseCreated"),
                        new TypedBy("CourseChanged")
                    ),
                    new TaggedAs("courseId", "c7")
                )
            ),
            contains(
                new EventId("course-created-7"),
                new EventId("course-changed-7")
            )
        );
    }

    @Test
    public final void isolatesNamespaces() {
        assertThat(
            "namespace must delimit observations",
            this.idsObservedInOneNamespace(this.store()),
            contains(new EventId("course-created-7"))
        );
    }

    @Test
    public final void treatsAfterPositionAsExclusive() {
        assertThat(
            "events(namespace, filter, after) must exclude the after Position itself",
            this.idsAfterFirstPosition(this.store()),
            contains(
                new EventId("course-removed-7"),
                new EventId("course-created-8"),
                new EventId("course-changed-7")
            )
        );
    }

    @Test
    public final void assignsMonotonicPositions() {
        assertThat(
            "stored events must appear in monotonically increasing Position order",
            this.positionsOfSampleHistory(this.store()),
            contains(
                new Position(1L),
                new Position(2L),
                new Position(3L),
                new Position(4L)
            )
        );
    }

    @Test
    public final void returnsEmptyObservationWhenNothingMatches() {
        assertThat(
            "an observation with no matching events must be empty",
            this.idsMatching(this.store(), new TypedBy("ProfessorAssigned")),
            is(emptyIterable())
        );
    }

    @Test
    public final void preservesEventOrderAfterFiltering() {
        assertThat(
            "filtering must preserve original Position order",
            this.idsMatching(
                this.store(),
                new AnyEvents(
                    new TypedBy("CourseChanged"),
                    new TypedBy("CourseCreated")
                )
            ),
            contains(
                new EventId("course-created-7"),
                new EventId("course-created-8"),
                new EventId("course-changed-7")
            )
        );
    }

    @Test
    public final void preservesBatchAppendOrder() {
        assertThat(
            "tail append must persist a batch in the supplied relative order",
            this.idsAfterBatchAppend(this.store()),
            contains(
                new EventId("student-enrolled-11"),
                new EventId("course-created-7"),
                new EventId("course-removed-7")
            )
        );
    }

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
            is(instanceOf(StaleTailException.class))
        );
    }

    @Test
    public final void explainsStaleTailConflictWithStoredEvent() {
        assertThat(
            "StaleTailException must expose the event that invalidated the tail",
            ((StaleTailException) this.failureOf(
                () -> this.reuseTailAfterRelevantAppend(this.store())
            )).conflict().event().id(),
            is(equalTo(new EventId("course-created-7")))
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
            is(instanceOf(IllegalArgumentException.class))
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
            "Subscription.next(count) must ignore non-matching wake-ups and complete on a matching event",
            this.subscriptionStatesAroundAppends(this.store()),
            contains(false, false, true)
        );
    }

    @Test
    public final void keepsSubscriptionResultsBounded() {
        assertThat(
            "StoredEvents returned by a subscription must stay bounded after later appends",
            this.repeatedSubscriptionResultAfterLaterAppend(this.store()),
            contains(
                List.of(new EventId("course-created-7")),
                List.of(new EventId("course-created-7"))
            )
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
    public final void ignoresEventsWithoutLatestByTag() {
        assertThat(
            "LatestBy must ignore events that do not carry the requested tag",
            this.idsAfterLatestByWithUntaggedEvent(this.store()),
            contains(new EventId("course-created-7"))
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
    public final void keepsReducedEventsInsideOriginalInput() {
        assertThat(
            "a reduction result must always be a subsequence of the original bounded observation",
            this.reducedIdsContainedInOriginalIds(this.store()),
            is(equalTo(true))
        );
    }

    @Test
    public final void doesNotAlterSurvivingStoredEventsAfterReduction() {
        assertThat(
            "a reduction must select StoredEvents without changing their public stored facts",
            this.reducedEventFacts(this.store()),
            contains(
                "course-created-8|CourseCreated|courseId=c8|3|true",
                "course-changed-7|CourseChanged|courseId=c7|4|true"
            )
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
    public final void preservesTailSemanticsAfterReduction() {
        assertThat(
            "a reduced observation tail must keep the original dependency boundary",
            this.failureOf(() -> this.reuseReducedTailAfterOriginalDependencyConflict(this.store())),
            is(instanceOf(StaleTailException.class))
        );
    }

    private Iterable<EventId> idsMatching(final EventStore store, final EventFilter filter) {
        return this.ids(this.filterHistory(store, new Namespace("filter-algebra")).reduce(new Matching(filter)));
    }

    private StoredEvents filterHistory(final EventStore store, final Namespace namespace) {
        this.append(
            store,
            namespace,
            new SampleEvent("course-created-7", "CourseCreated", "courseId", "c7"),
            new SampleEvent("course-removed-7", "CourseRemoved", "courseId", "c7"),
            new SampleEvent("student-enrolled-11", "StudentEnrolled", "studentId", "s11"),
            new SampleEvent("course-created-8", "CourseCreated", "courseId", "c8"),
            new SampleEvent("course-changed-7", "CourseChanged", "courseId", "c7")
        );
        return store.events(namespace, this.sampleEvents());
    }

    private Iterable<EventId> idsObservedInOneNamespace(final EventStore store) {
        this.append(
            store,
            new Namespace("namespace-a"),
            new SampleEvent("course-created-7", "CourseCreated", "courseId", "c7")
        );
        this.append(
            store,
            new Namespace("namespace-b"),
            new SampleEvent("course-created-8", "CourseCreated", "courseId", "c8")
        );
        return this.ids(store.events(new Namespace("namespace-a"), new TypedBy("CourseCreated")));
    }

    private Iterable<EventId> idsAfterFirstPosition(final EventStore store) {
        return this.ids(
            store.events(
                new Namespace("exclusive-after"),
                this.sampleEvents(),
                this.firstPosition(this.history(store, new Namespace("exclusive-after")))
            )
        );
    }

    private Iterable<Position> positionsOfSampleHistory(final EventStore store) {
        return this.positions(this.history(store, new Namespace("monotonic-positions")));
    }

    private Iterable<EventId> idsAfterBatchAppend(final EventStore store) {
        final Namespace namespace = new Namespace("batch-append");
        this.append(
            store,
            namespace,
            new SampleEvent("student-enrolled-11", "StudentEnrolled", "studentId", "s11"),
            new SampleEvent("course-created-7", "CourseCreated", "courseId", "c7"),
            new SampleEvent("course-removed-7", "CourseRemoved", "courseId", "c7")
        );
        return this.ids(store.events(namespace, this.sampleEvents()));
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

    private Iterable<Boolean> subscriptionStatesAroundAppends(final EventStore store) {
        final Namespace namespace = new Namespace("subscription-waiting");
        final var stage = store.events(namespace, new TypedBy("CourseCreated"))
            .follow()
            .next(1)
            .toCompletableFuture();
        final boolean before = stage.isDone();
        this.append(store, namespace, new SampleEvent("student-enrolled-11", "StudentEnrolled", "studentId", "s11"));
        final boolean afterIrrelevant = stage.isDone();
        this.append(store, namespace, new SampleEvent("course-created-7", "CourseCreated", "courseId", "c7"));
        return List.of(before, afterIrrelevant, stage.isDone());
    }

    private Iterable<List<EventId>> repeatedSubscriptionResultAfterLaterAppend(final EventStore store) {
        final Namespace namespace = new Namespace("subscription-bounded-result");
        final var stage = store.events(namespace, new TypedBy("CourseCreated")).follow().next(1);
        this.append(store, namespace, new SampleEvent("course-created-7", "CourseCreated", "courseId", "c7"));
        final StoredEvents result = this.completed(stage);
        this.append(store, namespace, new SampleEvent("course-created-8", "CourseCreated", "courseId", "c8"));
        return List.of(this.ids(result), this.ids(result));
    }

    private Iterable<EventId> idsAfterLatestReduction(final EventStore store) {
        return this.ids(this.history(store, new Namespace("latest")).reduce(new Latest()));
    }

    private Iterable<EventId> idsAfterLatestByReduction(final EventStore store) {
        return this.ids(
            this.history(store, new Namespace("latest-by")).reduce(new LatestBy(new TagName("courseId")))
        );
    }

    private Iterable<EventId> idsAfterLatestByWithUntaggedEvent(final EventStore store) {
        final Namespace namespace = new Namespace("latest-by-untagged");
        this.append(
            store,
            namespace,
            new SampleEvent("course-created-7", "CourseCreated", "courseId", "c7"),
            new SampleEvent("academic-year-opened", "AcademicYearOpened")
        );
        return this.ids(
            store.events(
                namespace,
                new AnyEvents(new TypedBy("CourseCreated"), new TypedBy("AcademicYearOpened"))
            ).reduce(new LatestBy(new TagName("courseId")))
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

    private boolean reducedIdsContainedInOriginalIds(final EventStore store) {
        final StoredEvents history = this.history(store, new Namespace("subsequence-reduction"));
        return this.ids(history).containsAll(
            this.ids(history.reduce(new LatestBy(new TagName("courseId"))))
        );
    }

    private Iterable<String> reducedEventFacts(final EventStore store) {
        final List<String> facts = new ArrayList<>();
        for (
            final StoredEvent event
                : this.history(
                    store,
                    new Namespace("surviving-event-facts")
                ).reduce(new LatestBy(new TagName("courseId")))
        ) {
            facts.add(
                event.id()
                    + "|" + event.type()
                    + "|" + StreamSupport.stream(event.tags().spliterator(), false)
                        .map(Object::toString)
                        .findFirst()
                        .orElse("")
                    + "|" + event.position()
                    + "|" + event.storedAt().getClass().equals(java.time.Instant.class)
            );
        }
        return facts;
    }

    private Iterable<List<EventId>> idsFromOrderedReductionPipelines(final EventStore store) {
        final StoredEvents history = this.history(store, new Namespace("ordered-reductions"));
        return List.of(
            this.ids(history.reduce(new Matching(new TypedBy("CourseCreated"))).reduce(new LatestBy(new TagName("courseId")))),
            this.ids(history.reduce(new LatestBy(new TagName("courseId"))).reduce(new Matching(new TypedBy("CourseCreated"))))
        );
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

    private Position firstPosition(final StoredEvents events) {
        return events.iterator().next().position();
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

    private Throwable failureOf(final Action action) {
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
        void run() throws Exception;
    }

    private record SampleEvent(EventId id, EventType type, Tags tags) implements Event {

        SampleEvent(final String id, final String type) {
            this(new EventId(id), new EventType(type), new EventTags());
        }

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
