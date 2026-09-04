package com.toolbox.tools.editor;

import java.util.Objects;

public final class EditorShellController {
    private final BubbleController bubbleController;
    private final EdgePanelFactory edgePanelFactory;
    private EditorMode mode = EditorMode.EDIT;
    private EditorFunction activeFunction = EditorFunction.UI;
    private boolean editEnabled = true;
    private boolean liveCapability;
    private String selectedObjectId;

    public EditorShellController(
            BubbleController bubbleController,
            EdgePanelFactory edgePanelFactory
    ) {
        this.bubbleController = Objects.requireNonNull(
                bubbleController,
                "bubbleController"
        );
        this.edgePanelFactory = Objects.requireNonNull(
                edgePanelFactory,
                "edgePanelFactory"
        );
    }

    public synchronized void setEditEnabled(boolean enabled) {
        editEnabled = enabled;
        if (!enabled) {
            selectedObjectId = null;
        }
    }

    public synchronized void selectObject(String objectId) {
        if (!editEnabled || mode != EditorMode.EDIT) {
            throw new IllegalStateException("selection unavailable");
        }
        selectedObjectId = com.toolbox.tools.core.StableId.require(
                objectId,
                "objectId"
        );
    }

    public synchronized void clearSelection() {
        selectedObjectId = null;
    }

    public synchronized void activateFunction(EditorFunction function) {
        activeFunction = Objects.requireNonNull(function, "function");
        selectedObjectId = null;
    }

    public synchronized void setLiveCapability(boolean available) {
        liveCapability = available;
        if (!available && mode == EditorMode.LIVE) {
            mode = EditorMode.EDIT;
        }
    }

    public synchronized void setMode(EditorMode next) {
        Objects.requireNonNull(next, "next");
        if (next == EditorMode.LIVE && !liveCapability) {
            throw new IllegalStateException("LIVE_CAPABILITY_UNAVAILABLE");
        }
        mode = next;
        if (next != EditorMode.EDIT) {
            selectedObjectId = null;
        }
    }

    public synchronized boolean editorOverlayVisible() {
        return mode == EditorMode.EDIT;
    }

    public synchronized EdgePanelModel edgePanel(
            VisualCapabilitySet capabilities
    ) {
        return edgePanelFactory.create(
                activeFunction,
                editEnabled,
                selectedObjectId != null,
                capabilities == null
                        ? VisualCapabilitySet.defaultEditable()
                        : capabilities
        );
    }

    public synchronized void emergencyReset() {
        mode = EditorMode.EDIT;
        activeFunction = EditorFunction.UI;
        editEnabled = true;
        liveCapability = false;
        selectedObjectId = null;
        bubbleController.reset();
    }

    public BubbleController bubbleController() { return bubbleController; }
    public synchronized EditorMode mode() { return mode; }
    public synchronized EditorFunction activeFunction() { return activeFunction; }
    public synchronized boolean editEnabled() { return editEnabled; }
    public synchronized String selectedObjectId() { return selectedObjectId; }
    public synchronized boolean liveCapability() { return liveCapability; }
}
