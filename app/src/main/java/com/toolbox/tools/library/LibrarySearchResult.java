package com.toolbox.tools.library;

public final class LibrarySearchResult {
    private final LibraryKey key;
    private final String labelIndonesia;

    public LibrarySearchResult(LibraryKey key, String labelIndonesia) {
        this.key = key;
        this.labelIndonesia = labelIndonesia;
    }

    public LibraryKey key() { return key; }
    public String labelIndonesia() { return labelIndonesia; }
}
