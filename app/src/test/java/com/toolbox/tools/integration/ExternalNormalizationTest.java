package com.toolbox.tools.integration;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import static org.junit.Assert.*;

public final class ExternalNormalizationTest {
    @Test
    public void normalizationIsDeterministicStableAndBounded() {
        ExternalNormalizer normalizer = new ExternalNormalizer();
        ExternalSnapshot snapshot = new ExternalSnapshot(
                "cursor.1",
                1,
                Arrays.asList(
                        new ExternalRawRecord(
                                "BETA",
                                new LinkedHashMap<String,String>() {{
                                    put("z", "2");
                                    put("a", "1");
                                }}
                        ),
                        new ExternalRawRecord(
                                "alpha",
                                Collections.singletonMap("title", "Contoh")
                        )
                )
        );

        NormalizationResult result =
                normalizer.normalize("adapter.demo", snapshot);

        assertTrue(result.isPass());
        assertEquals(2, result.records().size());
        assertEquals(
                "adapter.demo.item.alpha",
                result.records().get(0).stableId()
        );
        assertEquals(
                Arrays.asList("a", "z"),
                new java.util.ArrayList<>(
                        result.records().get(1).fields().keySet()
                )
        );
    }

    @Test
    public void duplicateExternalIdentityFailsClosed() {
        ExternalSnapshot snapshot = new ExternalSnapshot(
                "cursor.dup",
                2,
                Arrays.asList(
                        new ExternalRawRecord(
                                "same",
                                Collections.singletonMap("title", "A")
                        ),
                        new ExternalRawRecord(
                                "same",
                                Collections.singletonMap("title", "B")
                        )
                )
        );

        NormalizationResult result =
                new ExternalNormalizer().normalize("adapter.demo", snapshot);

        assertFalse(result.isPass());
        assertTrue(result.diagnostics().get(0)
                .startsWith("DUPLICATE_EXTERNAL_ID:"));
    }

    @Test
    public void externalBudgetsRejectOversizeInput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExternalSnapshot(
                        "cursor.big",
                        1,
                        Collections.nCopies(
                                ExternalSnapshot.MAX_RECORDS + 1,
                                new ExternalRawRecord(
                                        "x",
                                        Collections.emptyMap()
                                )
                        )
                )
        );

        StringBuilder big = new StringBuilder();
        for (int i = 0; i < ExternalRawRecord.MAX_VALUE_LENGTH + 1; i++) {
            big.append('x');
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExternalRawRecord(
                        "large",
                        Collections.singletonMap(
                                "value",
                                big.toString()
                        )
                )
        );
    }
}
