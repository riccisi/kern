package it.riccisi.kern.api.append;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.riccisi.kern.api.event.EventData;
import it.riccisi.kern.api.query.EventQuery;
import it.riccisi.kern.api.query.QueryItem;
import it.riccisi.kern.api.value.ContentType;
import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.EventTag;
import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.IdempotencyKey;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.SequencePosition;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AppendContractsTest {
    private static final EventTag STUDENT = new EventTag("student", "S1");
    private static final EventTag COURSE = new EventTag("course", "C1");
    private static final EventType ENROLLED = new EventType("StudentEnrolled.v1");
    private static final EventType ADDRESS_CHANGED = new EventType("StudentAddressChanged.v1");

    @Test
    void matchesQueryItemByTypeAndEveryRequiredTag() {
        QueryItem item = new QueryItem(Set.of(ENROLLED), Set.of(STUDENT, COURSE));

        assertThat(item.matches(event(ENROLLED, STUDENT, COURSE))).isTrue();
        assertThat(item.matches(event(ENROLLED, STUDENT))).isFalse();
        assertThat(item.matches(event(ADDRESS_CHANGED, STUDENT, COURSE))).isFalse();
    }

    @Test
    void combinesQueryItemsWithOrSemantics() {
        EventQuery query = new EventQuery(List.of(
            new QueryItem(Set.of(ENROLLED), Set.of(COURSE)),
            new QueryItem(Set.of(ADDRESS_CHANGED), Set.of(STUDENT))
        ));

        assertThat(query.matches(event(ENROLLED, COURSE))).isTrue();
        assertThat(query.matches(event(ADDRESS_CHANGED, STUDENT))).isTrue();
        assertThat(query.matches(event(ADDRESS_CHANGED, COURSE))).isFalse();
    }

    @Test
    void emptyQueryMatchesEveryEvent() {
        assertThat(new EventQuery(List.of()).matchingItem(event(ADDRESS_CHANGED, STUDENT)))
            .isEqualTo(OptionalInt.of(0));
    }

    @Test
    void keepsAppendConditionAsQueryAndObservedPosition() {
        EventQuery query = new EventQuery(List.of(new QueryItem(Set.of(ENROLLED), Set.of(COURSE))));
        AppendCondition condition = new AppendCondition(query, new SequencePosition(42));

        assertThat(condition.failIfEventsMatch()).isEqualTo(query);
        assertThat(condition.after()).isEqualTo(new SequencePosition(42));
    }

    @Test
    void copiesMutableInputsAtApiBoundary() {
        List<EventData> events = new ArrayList<>();
        events.add(event(ENROLLED, STUDENT, COURSE));

        AppendRequest request = new AppendRequest(
            new Namespace("education"),
            events,
            new AppendCondition(new EventQuery(List.of()), new SequencePosition(0)),
            new IdempotencyKey("append-s1-c1")
        );
        events.clear();

        assertThat(request.events()).hasSize(1);
    }

    @Test
    void rejectsAppendResultWithReversedPositions() {
        assertThatThrownBy(() -> new AppendResult(new SequencePosition(9), new SequencePosition(8), false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("to position must not be before from position");
    }

    private static EventData event(final EventType type, final EventTag... tags) {
        return new EventData(
            new EventId(UUID.nameUUIDFromBytes(type.value().getBytes(StandardCharsets.UTF_8))),
            type,
            Set.of(tags),
            new ContentType("application/json"),
            "{}".getBytes(StandardCharsets.UTF_8),
            "{}".getBytes(StandardCharsets.UTF_8)
        );
    }
}
