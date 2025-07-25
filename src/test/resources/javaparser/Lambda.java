package javaparser;

import java.util.HashSet;
import java.util.Set;

public class Lambda {

	public static void test(int x) {

	}

	public static void main(String[] args) {
		Set<Integer> set = new HashSet<>();
		set.forEach(x -> {
			test(x);
		});

		set.forEach(System.out::println);
	}
}