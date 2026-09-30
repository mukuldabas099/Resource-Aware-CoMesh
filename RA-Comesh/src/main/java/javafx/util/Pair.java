package javafx.util;

import java.io.Serializable;
import java.util.Objects;

/**
 * Minimal stand-in for javafx.util.Pair, used so the CoMesh simulator can be
 * compiled/run without pulling in the full JavaFX SDK (which is unavailable
 * in this offline build environment). API-compatible with the subset of
 * javafx.util.Pair used by this codebase (constructor + getKey/getValue).
 */
public class Pair<K, V> implements Serializable {
    private final K key;
    private final V value;

    public Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    @Override
    public String toString() {
        return key + "=" + value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pair)) return false;
        Pair<?, ?> p = (Pair<?, ?>) o;
        return Objects.equals(key, p.key) && Objects.equals(value, p.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }
}
