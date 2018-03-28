package ru.ifmo.rain.dovzhik.concurrent;

import info.kgeorgiy.java.advanced.concurrent.ListIP;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IterativeParallelism implements ListIP {
    private static void joinThreads(final List<Thread> threads) throws InterruptedException {
        InterruptedException exception = null;
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                if (exception == null) {
                    exception = new InterruptedException("Not all threads joined");
                }
                exception.addSuppressed(e);
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    private static <T, R> R baseTask(int threads, final List<? extends T> values,
                                     final Function<? super Stream<? extends T>, ? extends R> task,
                                     final Function<? super Stream<? extends R>, ? extends R> ansCollector)
            throws InterruptedException {
        if (threads <= 0) {
            throw new IllegalArgumentException("Number of threads must be positive");
        }
        threads = Math.max(1, Math.min(threads, values.size()));
        final List<Thread> workers = new ArrayList<>(Collections.nCopies(threads, null));
        final List<R> res = new ArrayList<>(Collections.nCopies(threads, null));
        final int blockSize = values.size() / threads;
        int rest = values.size() % threads;
        int pr = 0;
        for (int i = 0; i < threads; i++) {
            final int l = pr;
            final int r = l + blockSize + (rest-- > 0 ? 1 : 0);
            final int pos = i;
            pr = r;
            workers.set(i, new Thread(() -> res.set(pos, task.apply(values.subList(l, r).stream()))));
            workers.get(i).start();
        }
        joinThreads(workers);
        return ansCollector.apply(res.stream());
    }

    @Override
    public <T> T maximum(int threads, List<? extends T> values, Comparator<? super T> comparator) throws InterruptedException {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Unable to handle empty list");
        }
        final Function<Stream<? extends T>, ? extends T> streamMax = stream -> stream.max(comparator).get();
        return baseTask(threads, values, streamMax, streamMax);
    }

    @Override
    public <T> T minimum(int threads, List<? extends T> values, Comparator<? super T> comparator) throws InterruptedException {
        return maximum(threads, values, Collections.reverseOrder(comparator));
    }

    @Override
    public <T> boolean all(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return baseTask(threads, values,
                stream -> stream.allMatch(predicate),
                stream -> stream.allMatch(Boolean::booleanValue)
        );
    }

    @Override
    public <T> boolean any(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return !all(threads, values, elem -> !predicate.test(elem));
    }

    @Override
    public String join(int threads, List<?> values) throws InterruptedException {
        return baseTask(threads, values,
                stream -> stream.map(Object::toString).collect(Collectors.joining()),
                stream -> stream.collect(Collectors.joining())
        );
    }

    @Override
    public <T> List<T> filter(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return baseTask(threads, values,
                stream -> stream.filter(predicate).collect(Collectors.toList()),
                stream -> stream.flatMap(Collection::stream).collect(Collectors.toList())
        );
    }

    @Override
    public <T, U> List<U> map(int threads, List<? extends T> values, Function<? super T, ? extends U> f) throws InterruptedException {
        return baseTask(threads, values,
                stream -> stream.map(f).collect(Collectors.toList()),
                stream -> stream.flatMap(Collection::stream).collect(Collectors.toList())
        );
    }
}
