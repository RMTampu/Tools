package com.toolbox.tools.library;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class TemplateAndLibraryTest {
    @Test
    public void defaultLibraryUsesExactIdentityAndBahasaIndonesiaSearch() {
        LibraryManager library = DefaultLibraryFactory.create();

        Object exact = library.resolveExact(
                new LibraryKey(
                        LibraryItemType.COMPONENT,
                        "component.button",
                        VersionNumber.parse("1.0.0")
                )
        );
        assertNotNull(exact);

        List<LibrarySearchResult> byLabel = library.search("Tombol");
        assertTrue(byLabel.stream().anyMatch(
                item -> item.key().stableId().equals("component.button")
        ));

        LibraryKey key = new LibraryKey(
                LibraryItemType.COMPONENT,
                "component.button",
                VersionNumber.parse("1.0.0")
        );
        library.markFavorite(key, true);
        library.markRecent(key);
        assertTrue(library.favorites().contains(key));
        assertEquals(key, library.recent().get(0));
    }

    @Test
    public void templateInsertionRemapsIdentityAndDoesNotBecomeLinkedInstance() {
        LibraryManager library = DefaultLibraryFactory.create();
        TemplateDefinition template = (TemplateDefinition) library.resolveExact(
                new LibraryKey(
                        LibraryItemType.TEMPLATE,
                        "template.screen.basic",
                        VersionNumber.parse("1.0.0")
                )
        );

        TemplateInstantiationPlan first = new TemplateInstantiationPlan(
                template,
                "insert.one"
        );
        TemplateInstantiationPlan second = new TemplateInstantiationPlan(
                template,
                "insert.two"
        );

        String original = template.internalObjectIds().iterator().next();
        assertNotEquals(
                first.identityMap().get(original),
                second.identityMap().get(original)
        );
        assertTrue(first.identityMap().get(original).startsWith("insert.one."));
    }

    @Test
    public void missingTemplateDependencyBlocksReady() {
        TemplateDefinition template = new TemplateDefinition(
                "template.invalid",
                "Template Tidak Valid",
                VersionNumber.parse("1.0.0"),
                CatalogLifecycle.DRAFT,
                new LinkedHashSet<>(Collections.singletonList("object.one")),
                Collections.singletonList(
                        new DependencyRef(
                                "component.missing",
                                VersionRange.majorCompatible(
                                        VersionNumber.parse("1.0.0")
                                ),
                                true
                        )
                ),
                Collections.emptyList()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new TemplateRegistry().publishReady(
                        template,
                        new DependencyResolver(),
                        new ComponentRegistry(),
                        new AssetRegistry()
                )
        );
    }
}
