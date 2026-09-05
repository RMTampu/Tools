package com.toolbox.tools.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class FileRuntimeStateStore implements RuntimeStateStore {
    private final File file;
    private final Map<String, String> values = new LinkedHashMap<>();

    public FileRuntimeStateStore(File file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
        load();
    }

    @Override
    public synchronized String get(String key) {
        return values.get(StableId.require(key, "runtimeStateKey"));
    }

    @Override
    public synchronized void put(String key, String value) {
        values.put(
                StableId.require(key, "runtimeStateKey"),
                java.util.Objects.requireNonNull(value, "value")
        );
        persist();
    }

    @Override
    public synchronized void remove(String key) {
        values.remove(StableId.require(key, "runtimeStateKey"));
        persist();
    }

    @Override
    public synchronized Map<String, String> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private void load() {
        values.clear();
        if (!file.isFile()) return;
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(file)) {
            properties.load(input);
            for (String key : properties.stringPropertyNames()) {
                values.put(key, properties.getProperty(key));
            }
        } catch (IOException error) {
            throw new IllegalStateException(
                    "secure runtime metadata tidak dapat dibaca",
                    error
            );
        }
    }

    private void persist() {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException(
                    "secure runtime metadata directory unavailable"
            );
        }
        File pending = new File(file.getPath() + ".pending");
        Properties properties = new Properties();
        properties.putAll(values);
        try (FileOutputStream output = new FileOutputStream(pending)) {
            properties.store(output, "ToolBox secure runtime metadata");
            output.flush();
            output.getFD().sync();
        } catch (IOException error) {
            throw new IllegalStateException(
                    "secure runtime metadata write failed",
                    error
            );
        }
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException(
                    "secure runtime metadata replace failed"
            );
        }
        if (!pending.renameTo(file)) {
            throw new IllegalStateException(
                    "secure runtime metadata publish failed"
            );
        }
    }
}
