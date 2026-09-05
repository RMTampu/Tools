package com.toolbox.tools.core;

import java.util.Map;

public interface RuntimeStateStore {
    String get(String key);
    void put(String key, String value);
    void remove(String key);
    Map<String, String> snapshot();
}
