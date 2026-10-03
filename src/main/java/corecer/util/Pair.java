package corecer.util;

import java.util.Objects;

/** Immutable pair used in place of JavaFX's GUI-library dependency. */
public record Pair<K, V>(K key, V value) {
    public K getKey() { return key; }
    public V getValue() { return value; }
    @Override public int hashCode() { return Objects.hashCode(key) * 13 + Objects.hashCode(value); }
    @Override public String toString() { return key + "=" + value; }
}
