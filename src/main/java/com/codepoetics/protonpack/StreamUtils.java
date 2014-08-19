package com.codepoetics.protonpack;

import com.codepoetics.protonpack.iterators.SkipWhileIterator;
import com.codepoetics.protonpack.iterators.TakeWhileIterator;
import com.codepoetics.protonpack.iterators.UnfoldIterator;
import com.codepoetics.protonpack.iterators.ZippingIterator;

import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.*;
import java.util.stream.Stream;

public final class StreamUtils {

    private StreamUtils() {

    }

    public static Stream<Integer> indices() {
        return Stream.iterate(0, l -> l + 1);
    }

    public static <T> Stream<ZippedPair<Integer, T>> zipWithIndex(Stream<T> source) {
        return zip(indices(), source);
    }

    public static <L, R> Stream<ZippedPair<L, R>> zip(Stream<L> lefts, Stream<R> rights) {
        return zip(lefts, rights, ZippedPair::of);
    }

    public static <L, R, O> Stream<O> zip(Stream<L> lefts, Stream<R> rights, BiFunction<L, R, O> combiner) {
        Spliterator<L> leftSpliterator = lefts.spliterator();
        Spliterator<R> rightSpliterator = rights.spliterator();

        int sharedCharacteristics =
                leftSpliterator.characteristics()
                        & rightSpliterator.characteristics()
                        & ~(Spliterator.DISTINCT | Spliterator.SORTED);

        boolean isParallel = lefts.isParallel() || rights.isParallel();

        Streamifier streamifier = ((sharedCharacteristics & Spliterator.SIZED) != 0
                ? Streamifier.streamifier(isParallel)
                .sized(Math.min(leftSpliterator.getExactSizeIfKnown(),
                        rightSpliterator.getExactSizeIfKnown()))
                : Streamifier.streamifier(isParallel).unsized())
                .withCharacteristics(sharedCharacteristics);

        return streamifier.<O>streamify(ZippingIterator.over(
                Spliterators.iterator(leftSpliterator),
                Spliterators.iterator(rightSpliterator),
                combiner));
    }

    public static <T> Stream<T> takeWhile(Stream<T> source, Predicate<T> condition) {
        Spliterator<T> spliterator = source.spliterator();
        int characteristics = spliterator.characteristics() & ~(Spliterator.SIZED);
        return Streamifier
                .streamifier(source.isParallel())
                .unsized()
                .withCharacteristics(characteristics)
                .streamify(TakeWhileIterator.over(
                        Spliterators.iterator(spliterator),
                        condition));
    }

    public static <T> Stream<T> takeUntil(Stream<T> source, Predicate<T> condition) {
        return takeWhile(source, condition.negate());
    }

    public static <T> Stream<T> skipWhile(Stream<T> source, Predicate<T> condition) {
        Spliterator<T> spliterator = source.spliterator();
        int characteristics = spliterator.characteristics() & ~(Spliterator.SIZED);
        return Streamifier
                .streamifier(source.isParallel())
                .unsized()
                .withCharacteristics(characteristics)
                .streamify(SkipWhileIterator.over(
                        Spliterators.iterator(spliterator),
                        condition));
    }

    public static <T> Stream<T> skipUntil(Stream<T> source, Predicate<T> condition) {
        return skipWhile(source, condition.negate());
    }

    public static <T> Stream<T> unfold(Supplier<T> supplier, Predicate<T> condition) {
        T seed = supplier.get();
        if (seed == null || !condition.test(seed)) {
            return Stream.empty();
        }
        return Streamifier.toStream(UnfoldIterator.over(seed, Generator.withCondition(supplier, condition)));
    }

    public static <T> Stream<T> unfold(T seed, UnaryOperator<T> operator, Predicate<T> condition) {
        if (seed == null || !condition.test(seed)) {
            return Stream.empty();
        }
        return Streamifier.toStream(UnfoldIterator.over(seed, Generator.withCondition(operator, condition)));
    }

    public static <T> Stream<T> unfold(T seed, Function<T, Optional<T>> generator) {
        if (seed == null) {
            return Stream.empty();
        }
        return Streamifier.toStream(UnfoldIterator.over(seed, generator));
    }
}
