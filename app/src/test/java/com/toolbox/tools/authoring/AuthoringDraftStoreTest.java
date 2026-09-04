package com.toolbox.tools.authoring;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class AuthoringDraftStoreTest {
    @Test
    public void draftRevisionIsMonotonicAndPublishRequiresValidation() {
        AuthoringDraftStore store = new AuthoringDraftStore();
        store.create(
                "draft.one",
                AuthoringSection.DATA,
                "data.items",
                Collections.singletonMap("field.name", "Awal")
        );

        AuthoringDraft edited = store.edit(
                "draft.one",
                Collections.singletonMap("field.name", "Baru")
        );
        assertEquals(1, edited.revision());
        assertEquals(DraftLifecycle.DRAFT, edited.lifecycle());

        assertThrows(
                IllegalStateException.class,
                () -> store.markPublished("draft.one")
        );

        AuthoringDraft validated = store.validate("draft.one");
        assertEquals(DraftLifecycle.VALIDATED, validated.lifecycle());

        AuthoringDraft published = store.markPublished("draft.one");
        assertEquals(DraftLifecycle.PUBLISHED, published.lifecycle());

        assertThrows(
                IllegalStateException.class,
                () -> store.edit(
                        "draft.one",
                        Collections.singletonMap("field.name", "Tidak Boleh")
                )
        );
        assertThrows(
                IllegalStateException.class,
                () -> store.discard("draft.one")
        );
    }

    @Test
    public void validationCanReturnToDraftAndHistoryIsBounded() {
        AuthoringDraftStore store = new AuthoringDraftStore();
        store.create(
                "draft.history",
                AuthoringSection.UI,
                "screen.home",
                Collections.singletonMap("field.value", "0")
        );

        for (int i = 1; i <= AuthoringDraftStore.MAX_HISTORY + 8; i++) {
            if (i % 5 == 0) {
                store.validate("draft.history");
            }
            store.edit(
                    "draft.history",
                    Collections.singletonMap(
                            "field.value",
                            String.valueOf(i)
                    )
            );
        }

        assertEquals(
                AuthoringDraftStore.MAX_HISTORY,
                store.history("draft.history").size()
        );
        assertEquals(
                AuthoringDraftStore.MAX_HISTORY + 8,
                store.get("draft.history").revision()
        );
        assertEquals(
                DraftLifecycle.DRAFT,
                store.get("draft.history").lifecycle()
        );
    }

    @Test
    public void discardIsTerminal() {
        AuthoringDraftStore store = new AuthoringDraftStore();
        store.create(
                "draft.discard",
                AuthoringSection.ASSET,
                "asset.preview",
                Collections.emptyMap()
        );
        store.discard("draft.discard");

        assertEquals(
                DraftLifecycle.DISCARDED,
                store.get("draft.discard").lifecycle()
        );
        assertThrows(
                IllegalStateException.class,
                () -> store.validate("draft.discard")
        );
    }
}
