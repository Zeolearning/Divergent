package com.github.divergent.analysis;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.ObjectIdentityEqualsVisitor;
import com.github.javaparser.ast.visitor.ObjectIdentityHashCodeVisitor;
import com.github.javaparser.utils.VisitorMap;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.github.divergent.utils.StringReps.*;
import static java.lang.String.format;

public class MapFact<K extends Node, V> {
	private final Map<K, V> map;

	public MapFact() {
		this.map = new VisitorMap<>(new ObjectIdentityHashCodeVisitor(), new ObjectIdentityEqualsVisitor());
	}

	public boolean hasKey(K key) {
		return map.containsKey(key);
	}

	public void put(K key, V value) {
		map.put(key, value);
	}

	public V get(K key) {
		return map.getOrDefault(key, null);
	}

	public void assign(K lhs, K rhs) {
		if (hasKey(rhs)) {
			put(lhs, get(rhs));
		}
	}

	public void ifKeyExists(K key, Consumer<V> action) {
		if (hasKey(key)) {
			action.accept(get(key));
		}
	}

	public void forEach(BiConsumer<K, V> action) {
		map.forEach(action);
	}

	@Override
	public String toString() {
		return this.toString(key -> true);
	}

	public String toString(Predicate<K> filter) {
		String str = map.keySet().stream()
				.filter(filter)
				.sorted(Comparator.comparing(x -> x.getBegin().get()))
				.map(key -> format("\t[%s -> %s]", key, map.get(key)))
				.collect(Collectors.joining(", \n"));

		if (!str.isEmpty()) {
			str = "\n" + str + "\n";
		}
		return format("MapFact{%s}", str);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		MapFact<?, ?> that = (MapFact<?, ?>) o;
		return Objects.equals(map, that.map);
	}

	@Override
	public int hashCode() {
		return Objects.hash(map);
	}
}
