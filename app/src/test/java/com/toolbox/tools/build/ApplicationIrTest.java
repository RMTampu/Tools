package com.toolbox.tools.build;

import com.toolbox.tools.core.AppKernel;

import org.junit.Test;

import static org.junit.Assert.*;

public final class ApplicationIrTest {
    @Test
    public void irIsDeterministicStableKeyedAndReadOnly()
            throws Exception {
        AppKernel kernel = AppKernel.createDefault();
        kernel.projectManager().putResource(
                "screen.ir.demo",
                "RAW_PAYLOAD_MUST_NOT_APPEAR"
        );
        kernel.projectManager().save();
        kernel.readyCoordinator().publishReady();

        com.toolbox.tools.core.ProjectState before =
                kernel.projectManager().current();
        ApplicationIr first =
                kernel.readyCoordinator().buildIr();
        ApplicationIr second =
                kernel.readyCoordinator().buildIr();

        assertEquals(
                ApplicationIr.CURRENT_IR_VERSION,
                first.irVersion()
        );
        assertEquals(first.sha256(), second.sha256());
        assertEquals(first.canonical(), second.canonical());
        assertTrue(first.sha256().matches("[0-9a-f]{64}"));
        assertTrue(first.canonical().contains(
                "resource|screen.ir.demo|"
        ));
        assertFalse(first.canonical().contains(
                "RAW_PAYLOAD_MUST_NOT_APPEAR"
        ));
        assertTrue(first.canonical().contains(
                "component|component.button|1.0.0"
        ));
        assertEquals(before, kernel.projectManager().current());
    }

    @Test
    public void irRequiresReadyLifecycle() {
        AppKernel kernel = AppKernel.createDefault();

        assertThrows(
                IllegalStateException.class,
                () -> kernel.readyCoordinator().buildIr()
        );
    }
}
