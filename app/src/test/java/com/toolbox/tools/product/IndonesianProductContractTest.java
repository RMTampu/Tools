package com.toolbox.tools.product;

import com.toolbox.tools.core.AppKernel;
import com.toolbox.tools.editor.EdgeItem;
import com.toolbox.tools.editor.EdgePanelModel;
import com.toolbox.tools.editor.EditorFunction;
import com.toolbox.tools.editor.VisualCapabilitySet;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public final class IndonesianProductContractTest {
    @Test
    public void labelUtamaEditorMenggunakanBahasaIndonesia() {
        AppKernel kernel = AppKernel.createDefault();

        assertEquals(
                "id",
                kernel.configStore().get("bahasaDefault", "")
        );
        assertEquals("ToolBox Sendiri", kernel.selfTargetDescriptor().labelIndonesia());

        kernel.editorEnvironment().shell().activateFunction(EditorFunction.LOGIC);
        EdgePanelModel logic = kernel.editorEnvironment()
                .shell()
                .edgePanel(VisualCapabilitySet.defaultEditable());
        assertEquals("Logika", logic.titleIndonesia());
        assertContains(logic, "Peristiwa");
        assertContains(logic, "Aksi");
        assertContains(logic, "Kondisi");
        assertContains(logic, "Alur");
        assertContains(logic, "Variabel");
        assertContains(logic, "Fungsi");

        kernel.editorEnvironment().shell().activateFunction(EditorFunction.BINDING);
        EdgePanelModel binding = kernel.editorEnvironment()
                .shell()
                .edgePanel(VisualCapabilitySet.defaultEditable());
        assertEquals("Pengikatan", binding.titleIndonesia());
        assertContains(binding, "Hubungkan Otomatis");
        assertContains(binding, "Masalah");
        assertContains(binding, "Penggunaan");
        assertContains(binding, "Riwayat");

        kernel.editorEnvironment().shell().activateFunction(EditorFunction.ASSET);
        EdgePanelModel asset = kernel.editorEnvironment()
                .shell()
                .edgePanel(VisualCapabilitySet.defaultEditable());
        assertEquals("Aset", asset.titleIndonesia());
        assertContains(asset, "Impor");
        assertContains(asset, "Pratinjau");
        assertContains(asset, "Kompatibilitas");
        assertContains(asset, "Dependensi");
    }

    @Test
    public void temaGelapNeonDanKatalogBawaanLengkap() {
        AppKernel kernel = AppKernel.createDefault();

        assertEquals(
                "#071016",
                kernel.productServices().themes()
                        .get("token.color.background")
        );
        assertEquals(
                "#00F0B5",
                kernel.productServices().themes()
                        .get("token.color.neon")
        );
        assertTrue(kernel.libraryManager().components().allReady().size() >= 18);
        assertTrue(kernel.libraryManager().templates().allReady().size() >= 4);
        assertTrue(kernel.libraryManager().assets().allReady().size() >= 5);
    }

    private static void assertContains(
            EdgePanelModel panel,
            String label
    ) {
        for (EdgeItem item : panel.items()) {
            if (label.equals(item.labelIndonesia())) return;
        }
        fail("Label tidak ditemukan: " + label);
    }
}
