package com.toolbox.tools.runtime;

import com.toolbox.tools.library.ComponentInstance;
import com.toolbox.tools.library.DefaultLibraryFactory;
import com.toolbox.tools.library.LibraryManager;
import com.toolbox.tools.library.VersionNumber;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class DataBindingTest {
    @Test
    public void pagingUsesStableDataItemKeyAndBoundedWorkingSubset() {
        DataSourceDefinition definition = new DataSourceDefinition(
                "data.items",
                "field.id",
                Arrays.asList(
                        new DataFieldDefinition(
                                "field.id",
                                ValueType.REFERENCE,
                                true
                        ),
                        new DataFieldDefinition(
                                "field.title",
                                ValueType.TEXT,
                                true
                        )
                )
        );
        InMemoryDataSource source = new InMemoryDataSource(definition);
        source.put(record("item.one", "Satu"));
        source.put(record("item.two", "Dua"));
        source.put(record("item.three", "Tiga"));

        assertEquals(
                "item.two",
                source.query(new PagedQuery(1, 1)).get(0).stableKey()
        );
        assertEquals(2, source.query(new PagedQuery(0, 2)).size());
        assertThrows(
                IllegalArgumentException.class,
                () -> source.put(record("item.one", "Duplikat"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PagedQuery(0, PagedQuery.MAX_PAGE_SIZE + 1)
        );
    }

    @Test
    public void bindingCompatibilityIsExactAndTwoWayCycleIsSuppressed() {
        LibraryManager library = DefaultLibraryFactory.create();
        DataSourceDefinition definition = new DataSourceDefinition(
                "data.items",
                "field.id",
                Arrays.asList(
                        new DataFieldDefinition(
                                "field.id",
                                ValueType.REFERENCE,
                                true
                        ),
                        new DataFieldDefinition(
                                "field.title",
                                ValueType.TEXT,
                                true
                        )
                )
        );
        ComponentInstance instance = new ComponentInstance(
                "instance.button",
                "component.button",
                VersionNumber.parse("1.0.0"),
                Collections.emptyMap()
        );
        BindingDefinition binding = new BindingDefinition(
                "binding.title",
                "data.items",
                "field.title",
                "instance.button",
                "property.text",
                ValueType.TEXT,
                BindingMode.TWO_WAY
        );

        assertTrue(new BindingValidator().isCompatible(
                binding,
                definition,
                instance,
                library.components()
        ));

        BindingCycleGuard guard = new BindingCycleGuard();
        ChangeToken token = new ChangeToken("origin.input", 1);
        assertTrue(guard.enter(token));
        assertFalse(guard.enter(token));
        guard.exit(token);
        assertTrue(guard.enter(token));
    }

    private static DataRecord record(String id, String title) {
        return new DataRecord(
                id,
                new java.util.LinkedHashMap<String, String>() {{
                    put("field.id", id);
                    put("field.title", title);
                }}
        );
    }
}
