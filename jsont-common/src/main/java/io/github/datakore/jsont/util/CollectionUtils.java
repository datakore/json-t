package io.github.datakore.jsont.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class CollectionUtils {

    public static boolean isEmpty(Collection<?> coll) {
        return coll == null || coll.isEmpty();
    }

    public static <T> List<T> asList(T... elements) {
        return Arrays.asList(elements);
    }

    public static <T> Set<T> asSet(T... elements) {
        return Stream.of(elements).collect(java.util.stream.Collectors.toSet());
    }
}
