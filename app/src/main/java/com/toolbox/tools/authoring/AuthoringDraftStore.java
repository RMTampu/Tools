package com.toolbox.tools.authoring;

import com.toolbox.tools.core.StableId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AuthoringDraftStore {
    public static final int MAX_HISTORY = 32;

    private final Map<String, AuthoringDraft> current = new LinkedHashMap<>();
    private final Map<String, Deque<AuthoringDraft>> history = new LinkedHashMap<>();

    public synchronized AuthoringDraft create(
            String draftId,
            AuthoringSection section,
            String targetId,
            Map<String, String> fields
    ) {
        String id = StableId.require(draftId, "draftId");
        if (current.containsKey(id)) {
            throw new IllegalArgumentException("draft already exists");
        }
        AuthoringDraft draft = new AuthoringDraft(
                id,
                section,
                targetId,
                0,
                DraftLifecycle.DRAFT,
                fields
        );
        current.put(id, draft);
        record(draft);
        return draft;
    }

    public synchronized AuthoringDraft edit(
            String draftId,
            Map<String, String> fields
    ) {
        AuthoringDraft previous = requireMutable(draftId);
        AuthoringDraft next = new AuthoringDraft(
                previous.draftId(),
                previous.section(),
                previous.targetId(),
                previous.revision() + 1,
                DraftLifecycle.DRAFT,
                fields
        );
        current.put(next.draftId(), next);
        record(next);
        return next;
    }

    public synchronized AuthoringDraft validate(String draftId) {
        AuthoringDraft previous = requireMutable(draftId);
        AuthoringDraft next = transition(previous, DraftLifecycle.VALIDATED);
        current.put(next.draftId(), next);
        record(next);
        return next;
    }

    public synchronized AuthoringDraft markPublished(String draftId) {
        AuthoringDraft previous = require(draftId);
        if (previous.lifecycle() != DraftLifecycle.VALIDATED) {
            throw new IllegalStateException("draft must be VALIDATED before publish");
        }
        AuthoringDraft next = transition(previous, DraftLifecycle.PUBLISHED);
        current.put(next.draftId(), next);
        record(next);
        return next;
    }

    public synchronized AuthoringDraft discard(String draftId) {
        AuthoringDraft previous = requireMutable(draftId);
        AuthoringDraft next = transition(previous, DraftLifecycle.DISCARDED);
        current.put(next.draftId(), next);
        record(next);
        return next;
    }

    public synchronized AuthoringDraft get(String draftId) {
        return current.get(StableId.require(draftId, "draftId"));
    }

    public synchronized List<AuthoringDraft> history(String draftId) {
        Deque<AuthoringDraft> values = history.get(
                StableId.require(draftId, "draftId")
        );
        return values == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }

    private AuthoringDraft require(String draftId) {
        AuthoringDraft draft = current.get(
                StableId.require(draftId, "draftId")
        );
        if (draft == null) {
            throw new IllegalArgumentException("draft unavailable");
        }
        return draft;
    }

    private AuthoringDraft requireMutable(String draftId) {
        AuthoringDraft draft = require(draftId);
        if (draft.lifecycle().terminal()) {
            throw new IllegalStateException("draft terminal");
        }
        return draft;
    }

    private static AuthoringDraft transition(
            AuthoringDraft previous,
            DraftLifecycle lifecycle
    ) {
        return new AuthoringDraft(
                previous.draftId(),
                previous.section(),
                previous.targetId(),
                previous.revision(),
                lifecycle,
                previous.fields()
        );
    }

    private void record(AuthoringDraft draft) {
        Deque<AuthoringDraft> values = history.computeIfAbsent(
                draft.draftId(),
                ignored -> new ArrayDeque<>()
        );
        values.addLast(Objects.requireNonNull(draft, "draft"));
        while (values.size() > MAX_HISTORY) {
            values.removeFirst();
        }
    }
}
