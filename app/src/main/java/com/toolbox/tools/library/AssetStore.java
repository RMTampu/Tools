package com.toolbox.tools.library;

import java.io.IOException;

public interface AssetStore {
    void importOriginal(AssetDescriptor descriptor, byte[] bytes) throws IOException;

    AssetStatus status(AssetDescriptor descriptor) throws IOException;

    byte[] readOriginal(AssetDescriptor descriptor) throws IOException;

    void relinkOriginal(AssetDescriptor descriptor, byte[] candidate) throws IOException;

    void writePreviewCache(AssetDescriptor descriptor, byte[] preview) throws IOException;

    void clearCache() throws IOException;

    boolean originalExists(AssetDescriptor descriptor);

    boolean previewExists(AssetDescriptor descriptor);
}
