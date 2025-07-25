package com.github.divergent.divide;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class StopWatch {
    private static StopWatch instance = null;
    private final Map<String, Long> watch;
    private final Map<String, Long> record;

    public static StopWatch getInstance() {
        if (instance == null) {
            instance = new StopWatch();
        }
        return instance;
    }

    private StopWatch() {
        this.watch = new HashMap<>();
        this.record = new LinkedHashMap<>();
    }

    public void mark(String phase) {
        assert !watch.containsKey(phase);
        watch.put(phase, System.currentTimeMillis());
    }

    public long finish(String phase) {
        long cost = System.currentTimeMillis() - watch.get(phase);
        record.put(phase, cost);
        return cost;
    }

    public long getTime(String phase) {
        return record.get(phase);
    }

    public String display() {
        return record.entrySet().stream()
                .map(entry -> String.format("Phase %s cost %dms", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    public void reset() {
        watch.clear();
        record.clear();
    }
}
