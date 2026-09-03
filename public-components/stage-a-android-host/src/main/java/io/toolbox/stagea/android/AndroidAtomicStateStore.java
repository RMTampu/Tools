package io.toolbox.stagea.android;

import android.content.Context;
import android.util.AtomicFile;

import io.toolbox.stagea.StageAContracts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class AndroidAtomicStateStore {
    public static final String DEFAULT_RELATIVE_PATH = "toolbox-stage-a/runtime-state.bin";

    private final Object lock = new Object();
    private final AtomicFile atomicFile;

    public AndroidAtomicStateStore(Context context) {
        this(context, DEFAULT_RELATIVE_PATH);
    }

    public AndroidAtomicStateStore(Context context, String relativePath) {
        if (context == null) throw new NullPointerException("context");
        if (relativePath == null || relativePath.isEmpty() || relativePath.startsWith("/")
                || relativePath.contains("..") || relativePath.contains("\\")) {
            throw new StageAContracts.StageAException("state.path.invalid", "State path must be app-private and relative");
        }
        File base = new File(context.getFilesDir(), relativePath);
        File parent = base.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory())) {
            throw new StageAContracts.StageAException("state.directory.unavailable", "State directory unavailable");
        }
        this.atomicFile = new AtomicFile(base);
    }

    public void put(String key, String value) {
        synchronized (lock) {
            Map<String, String> next = new LinkedHashMap<>(readMap());
            next.put(StateFileCodec.requireKey(key), StateFileCodec.requireValue(value));
            writeMap(next);
        }
    }

    public String get(String key) {
        synchronized (lock) {
            return readMap().get(StateFileCodec.requireKey(key));
        }
    }

    public void remove(String key) {
        synchronized (lock) {
            Map<String, String> next = new LinkedHashMap<>(readMap());
            if (next.remove(StateFileCodec.requireKey(key)) != null) writeMap(next);
        }
    }

    public Set<String> keys(String prefix) {
        synchronized (lock) {
            String safePrefix = prefix == null ? "" : prefix;
            if (safePrefix.length() > StateFileCodec.MAX_KEY_LENGTH) {
                throw new StageAContracts.StageAException("state.resource.limit", "State prefix too long");
            }
            java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
            for (String key : readMap().keySet()) if (key.startsWith(safePrefix)) result.add(key);
            return java.util.Collections.unmodifiableSet(result);
        }
    }

    public void clear() {
        synchronized (lock) {
            atomicFile.delete();
        }
    }

    private Map<String, String> readMap() {
        try {
            if (!atomicFile.getBaseFile().exists() && !new File(atomicFile.getBaseFile().getPath() + ".bak").exists()) {
                return java.util.Collections.emptyMap();
            }
            return StateFileCodec.decode(atomicFile.readFully());
        } catch (IOException failure) {
            throw new StageAContracts.StageAException("state.read.failed", "Unable to read durable state", failure);
        }
    }

    private void writeMap(Map<String, String> map) {
        byte[] payload = StateFileCodec.encode(map);
        FileOutputStream stream = null;
        try {
            stream = atomicFile.startWrite();
            stream.write(payload);
            stream.flush();
            stream.getFD().sync();
            atomicFile.finishWrite(stream);
        } catch (IOException failure) {
            if (stream != null) atomicFile.failWrite(stream);
            throw new StageAContracts.StageAException("state.write.failed", "Unable to atomically persist state", failure);
        }
    }
}
