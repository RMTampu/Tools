package com.toolbox.tools.core;

public enum UnsavedDecision {
    SAVE("Simpan"),
    DISCARD("Keluar Tanpa Simpan"),
    CANCEL("Batal");

    private final String label;

    UnsavedDecision(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
