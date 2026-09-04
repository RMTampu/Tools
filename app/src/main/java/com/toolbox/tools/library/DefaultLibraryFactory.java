package com.toolbox.tools.library;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

public final class DefaultLibraryFactory {
    private DefaultLibraryFactory() {
    }

    public static LibraryManager create() {
        ComponentRegistry components = new ComponentRegistry();
        AssetRegistry assets = new AssetRegistry();
        TemplateRegistry templates = new TemplateRegistry();
        DependencyResolver resolver = new DependencyResolver();
        ComponentValidator validator = new ComponentValidator();

        ComponentDefinition buttonDraft = new ComponentDefinition(
                "component.button",
                "text.component.button",
                "Tombol",
                "category.input",
                null,
                VersionNumber.parse("1.0.0"),
                CatalogLifecycle.DRAFT,
                "implementation.android.button",
                Arrays.asList(
                        new PropertyContract(
                                "property.text",
                                PropertyType.TEXT,
                                false,
                                true,
                                "Tombol",
                                Collections.emptySet()
                        ),
                        new PropertyContract(
                                "property.enabled",
                                PropertyType.BOOLEAN,
                                false,
                                true,
                                "true",
                                Collections.emptySet()
                        )
                ),
                Collections.singletonList(
                        new EventContract(
                                "event.click",
                                Collections.singleton("action.ui")
                        )
                ),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptyList()
        );
        components.publishReady(buttonDraft, validator, assets);

        TemplateDefinition emptyScreenDraft = new TemplateDefinition(
                "template.screen.basic",
                "Layar Dasar",
                VersionNumber.parse("1.0.0"),
                CatalogLifecycle.DRAFT,
                new LinkedHashSet<>(
                        Collections.singletonList("object.primary")
                ),
                Collections.singletonList(
                        new DependencyRef(
                                "component.button",
                                VersionRange.majorCompatible(
                                        VersionNumber.parse("1.0.0")
                                ),
                                true
                        )
                ),
                Collections.emptyList()
        );
        templates.publishReady(
                emptyScreenDraft,
                resolver,
                components,
                assets
        );

        return new LibraryManager(components, assets, templates);
    }
}
