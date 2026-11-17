package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNull;

public record Pair<K, V>(K key, V value) {

    public static <K, V> Pair<K, V> pair(K key, V value) {
        return new Pair<>(key, value);
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    @Override
    public @NotNull String toString() {
        return "(" + key + ", " + value + ")";
    }
}
