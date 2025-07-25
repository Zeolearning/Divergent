package com.github.divergent.analysis;

import com.github.divergent.utils.StringReps;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SetFact<E> implements Iterable<E> {
	private final Set<E> set;

	public SetFact() {
		this(Collections.emptySet());
	}

	@SafeVarargs
	public SetFact(E... elems) {
		this(Arrays.asList(elems));
	}

	public SetFact(Collection<E> collection) {
		this.set = new LinkedHashSet<>(collection);
	}

	public void add(E e) {
		set.add(e);
	}

	public void merge(SetFact<E> other) {
		set.addAll(other.set);
	}

	public SetFact<E> copy() {
		return new SetFact<>(set);
	}

	public boolean isEmpty() {
		return set.isEmpty();
	}

	@SafeVarargs
	public static <T> SetFact<T> of(T... elems) {
		return new SetFact<>(elems);
	}

	public Stream<E> stream() {
		return set.stream();
	}

	@Override
	public Iterator<E> iterator() {
		return set.iterator();
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		set.forEach(action);
	}

	public String toString() {
		String s = set.stream()
					.map(StringReps::objectToString)
					.collect(Collectors.joining(", "));
		return "{" + s + "}";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		SetFact<?> other = (SetFact<?>) o;
		return Objects.equals(set, other.set);
	}

	@Override
	public int hashCode() {
		return Objects.hash(set);
	}
}