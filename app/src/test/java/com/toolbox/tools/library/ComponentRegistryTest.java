package com.toolbox.tools.library;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class ComponentRegistryTest {
    @Test
    public void readyComponentHasCompleteManifestAndExactVersionPinning() {
        ComponentRegistry registry = new ComponentRegistry();
        AssetRegistry assets = new AssetRegistry();
        ComponentValidator validator = new ComponentValidator();

        ComponentDefinition v1 = component("1.0.0", Collections.emptyList());
        ComponentDefinition v2 = component("2.0.0", Collections.emptyList());

        registry.publishReady(v1, validator, assets);
        registry.publishReady(v2, validator, assets);

        ComponentInstance pinned = new ComponentInstance(
                "instance.button.primary",
                "component.button.test",
                VersionNumber.parse("1.0.0"),
                Collections.singletonMap("property.text", "Simpan")
        );

        assertTrue(pinned.isAvailable(registry));
        assertEquals(
                VersionNumber.parse("2.0.0"),
                registry.latestReady("component.button.test").version()
        );
        assertEquals(
                VersionNumber.parse("1.0.0"),
                registry.resolveExact(
                        "component.button.test",
                        pinned.componentVersion()
                ).version()
        );

        ComponentManifest manifest = registry.manifestExact(
                "component.button.test",
                VersionNumber.parse("1.0.0")
        );
        assertNotNull(manifest);
        assertTrue(manifest.verifies(registry.resolveExact(
                "component.button.test",
                VersionNumber.parse("1.0.0")
        )));
        assertEquals(64, manifest.checksum().length());
    }

    @Test
    public void unresolvedRequiredDependencyBlocksReady() {
        ComponentRegistry registry = new ComponentRegistry();
        ComponentDefinition dependent = component(
                "1.0.0",
                Collections.singletonList(
                        new DependencyRef(
                                "component.missing",
                                VersionRange.majorCompatible(
                                        VersionNumber.parse("1.0.0")
                                ),
                                true
                        )
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.publishReady(
                        dependent,
                        new ComponentValidator(),
                        new AssetRegistry()
                )
        );
    }

    @Test
    public void variantAndCompositeRemainBoundToExistingMasters() {
        ComponentRegistry registry = new ComponentRegistry();
        registry.publishReady(
                component("1.0.0", Collections.emptyList()),
                new ComponentValidator(),
                new AssetRegistry()
        );

        ComponentVariant variant = new ComponentVariant(
                "variant.button.primary",
                "component.button.test",
                VersionNumber.parse("1.0.0"),
                Collections.singletonMap("property.text", "Utama")
        );
        assertTrue(variant.isCompatible(registry));

        ComponentInstance child = new ComponentInstance(
                "instance.child",
                "component.button.test",
                VersionNumber.parse("1.0.0"),
                Collections.emptyMap()
        );
        CompositeComponentSpec composite = new CompositeComponentSpec(
                "component.composite.test",
                Collections.singletonMap("child.primary", child)
        );
        assertTrue(composite.isCompatible(registry));

        ComponentInstance unavailable = new ComponentInstance(
                "instance.missing",
                "component.unknown",
                VersionNumber.parse("1.0.0"),
                Collections.emptyMap()
        );
        assertFalse(unavailable.isAvailable(registry));
    }

    private static ComponentDefinition component(
            String version,
            java.util.List<DependencyRef> dependencies
    ) {
        return new ComponentDefinition(
                "component.button.test",
                "text.component.button.test",
                "Tombol Uji",
                "category.input",
                null,
                VersionNumber.parse(version),
                CatalogLifecycle.DRAFT,
                "implementation.test.button",
                Collections.singletonList(
                        new PropertyContract(
                                "property.text",
                                PropertyType.TEXT,
                                false,
                                true,
                                "Tombol",
                                Collections.emptySet()
                        )
                ),
                Collections.singletonList(
                        new EventContract(
                                "event.click",
                                Collections.singleton("action.ui")
                        )
                ),
                new StateContract(
                        new LinkedHashSet<>(
                                Arrays.asList("state.normal", "state.disabled")
                        )
                ),
                new BindingContract(
                        "binding.profile.default",
                        Collections.singleton("binding.text"),
                        true
                ),
                new AccessibilityContract(
                        "accessibility.role.button",
                        true,
                        true
                ),
                Collections.emptySet(),
                Collections.emptyList(),
                dependencies
        );
    }
}
