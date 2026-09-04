package com.toolbox.tools.library;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BuiltinComponentCatalog {
    private BuiltinComponentCatalog() {}

    public static List<ComponentDefinition> components() {
        List<ComponentDefinition> out = new ArrayList<>();
        out.add(component("button","Tombol","category.input","button",
                props(text("property.text","Tombol"), bool("property.enabled",true),
                        color("property.color","#00F0B5"), dim("property.radius","16")),
                events("event.click")));
        out.add(component("text","Teks","category.content","text",
                props(text("property.text","Teks"), color("property.color","#E8FFF8"),
                        dim("property.text.size","16"), enumProp("property.text.align","align.start","align.start","align.center","align.end")),
                events("event.tap")));
        out.add(component("input","Input Teks","category.input","input",
                props(text("property.hint","Masukkan teks"), text("property.value",""),
                        bool("property.enabled",true), enumProp("property.input.type","input.text","input.text","input.number","input.password")),
                events("event.change","event.focus")));
        out.add(component("image","Gambar","category.media","image",
                props(text("property.asset",""), enumProp("property.fit","fit.cover","fit.cover","fit.contain","fit.center"),
                        dim("property.radius","12")), events("event.tap")));
        out.add(component("icon","Ikon","category.media","icon",
                props(text("property.asset",""), color("property.color","#E8FFF8"),
                        dim("property.size","24")), events("event.tap")));
        out.add(component("container","Kontainer","category.layout","container",
                props(enumProp("property.layout","layout.column","layout.row","layout.column","layout.stack","layout.grid","layout.free"),
                        dim("property.spacing","8"), color("property.background","#0D1B24"),
                        dim("property.padding","12")), events()));
        out.add(component("row","Baris","category.layout","row",
                props(dim("property.spacing","8"), enumProp("property.align","align.center","align.start","align.center","align.end")), events()));
        out.add(component("column","Kolom","category.layout","column",
                props(dim("property.spacing","8"), enumProp("property.align","align.start","align.start","align.center","align.end")), events()));
        out.add(component("grid","Kisi","category.layout","grid",
                props(number("property.columns","2"), dim("property.spacing","8")), events()));
        out.add(component("list","Daftar","category.data","list",
                props(text("property.source","data.items"), number("property.page.size","20"),
                        bool("property.divider",true)), events("event.item.click")));
        out.add(component("card","Kartu","category.layout","card",
                props(color("property.background","#112832"), dim("property.radius","18"),
                        dim("property.padding","14"), dim("property.elevation","2")), events("event.tap")));
        out.add(component("switch","Sakelar","category.input","switch",
                props(bool("property.checked",false), text("property.label","Sakelar")), events("event.change")));
        out.add(component("checkbox","Kotak Centang","category.input","checkbox",
                props(bool("property.checked",false), text("property.label","Pilihan")), events("event.change")));
        out.add(component("radio","Pilihan Radio","category.input","radio",
                props(bool("property.checked",false), text("property.label","Pilihan")), events("event.change")));
        out.add(component("slider","Penggeser","category.input","slider",
                props(number("property.value","50"), number("property.min","0"), number("property.max","100")), events("event.change")));
        out.add(component("progress","Progres","category.feedback","progress",
                props(number("property.value","50"), number("property.max","100")), events()));
        out.add(component("divider","Pemisah","category.layout","divider",
                props(color("property.color","#244650"), dim("property.thickness","1")), events()));
        out.add(component("spacer","Ruang","category.layout","spacer",
                props(dim("property.width","8"), dim("property.height","8")), events()));
        return Collections.unmodifiableList(out);
    }

    public static List<TemplateDefinition> templates() {
        List<TemplateDefinition> out = new ArrayList<>();
        out.add(template("template.screen.basic","Layar Dasar",
                "component.container","component.text","component.button"));
        out.add(template("template.form.basic","Formulir Dasar",
                "component.container","component.text","component.input","component.button"));
        out.add(template("template.list.basic","Daftar Dasar",
                "component.container","component.text","component.list"));
        out.add(template("template.dashboard.neon","Dashboard Neon",
                "component.container","component.row","component.card","component.text","component.progress"));
        return Collections.unmodifiableList(out);
    }

    private static ComponentDefinition component(
            String suffix,
            String label,
            String category,
            String implementation,
            List<PropertyContract> properties,
            List<EventContract> events
    ) {
        return new ComponentDefinition(
                "component."+suffix,
                "text.component."+suffix,
                label,
                category,
                null,
                VersionNumber.parse("1.0.0"),
                CatalogLifecycle.DRAFT,
                "implementation.android."+implementation,
                properties,
                events,
                new StateContract(new LinkedHashSet<>(Arrays.asList(
                        "state.normal","state.pressed","state.focused",
                        "state.selected","state.disabled","state.error","state.loading"
                ))),
                new BindingContract(
                        "binding.profile."+suffix,
                        new LinkedHashSet<>(Arrays.asList(
                                "binding.text","binding.value","binding.visible","binding.enabled"
                        )),
                        true
                ),
                new AccessibilityContract(
                        "accessibility.role."+suffix,
                        true,
                        true
                ),
                Collections.emptySet(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    private static TemplateDefinition template(
            String id,
            String label,
            String... components
    ) {
        List<DependencyRef> deps = new ArrayList<>();
        Set<String> objects = new LinkedHashSet<>();
        int i=1;
        for(String component:components){
            deps.add(new DependencyRef(
                    component,
                    VersionRange.majorCompatible(VersionNumber.parse("1.0.0")),
                    true
            ));
            if ("template.screen.basic".equals(id) && i == 1) {
                objects.add("object.primary");
            } else {
                objects.add("object."+id.substring(id.lastIndexOf('.')+1)+"."+i);
            }
            i++;
        }
        return new TemplateDefinition(
                id,
                label,
                VersionNumber.parse("1.0.0"),
                CatalogLifecycle.DRAFT,
                objects,
                deps,
                Collections.emptyList()
        );
    }

    private static List<PropertyContract> props(PropertyContract... values) {
        return Arrays.asList(values);
    }

    private static List<EventContract> events(String... ids) {
        List<EventContract> out=new ArrayList<>();
        for(String id:ids){
            out.add(new EventContract(
                    id,
                    new LinkedHashSet<>(Arrays.asList(
                            "action.ui","action.navigation","action.data","action.dialog"
                    ))
            ));
        }
        return out;
    }

    private static PropertyContract text(String id,String value){
        return new PropertyContract(id,PropertyType.TEXT,true,true,value,Collections.emptySet());
    }
    private static PropertyContract bool(String id,boolean value){
        return new PropertyContract(id,PropertyType.BOOLEAN,false,true,String.valueOf(value),Collections.emptySet());
    }
    private static PropertyContract number(String id,String value){
        return new PropertyContract(id,PropertyType.NUMBER,false,true,value,Collections.emptySet());
    }
    private static PropertyContract dim(String id,String value){
        return new PropertyContract(id,PropertyType.DIMENSION,false,true,value,Collections.emptySet());
    }
    private static PropertyContract color(String id,String value){
        return new PropertyContract(id,PropertyType.COLOR,false,true,value,Collections.emptySet());
    }
    private static PropertyContract enumProp(String id,String value,String... choices){
        return new PropertyContract(
                id,PropertyType.ENUM,false,true,value,
                new LinkedHashSet<>(Arrays.asList(choices))
        );
    }
}
