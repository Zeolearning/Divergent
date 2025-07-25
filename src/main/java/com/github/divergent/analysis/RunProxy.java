package com.github.divergent.analysis;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static com.github.divergent.utils.StringReps.*;

public class RunProxy {
	public static class ExceptionLogger {
		private final File log;

		public ExceptionLogger(File log) {
			this.log = log;
		}

		@RuntimeType
		public Object intercept(@Origin Method method, @SuperCall Callable<?> callable) throws Exception {
			try {
				return callable.call();
			} catch (RuntimeException e) {
//				PrintStream out = new PrintStream(new FileOutputStream(log, true));
//				out.printf("%s:\t%s%n", getTimeStamp(), e.getMessage());
//				out.println(Arrays.stream(e.getStackTrace()).limit(3)
//							.map(Objects::toString).collect(Collectors.joining("\n")));
//				out.println();
//				out.close();
				// the type of default value should be consistent with the proxied method
				return null;
			}
		}
	}

	public static <T, E> T createProxy(Class<T> clazz, E interceptor, String[] names,
									   Class<?>[] paramTypes, Object[] args) {
		ElementMatcher.Junction<NamedElement> methods;
		if (names != null) {
			methods = ElementMatchers.namedOneOf(names);
		} else {
			methods = ElementMatchers.any();
		}
		try {
			return new ByteBuddy()
					.subclass(clazz)
					.method(methods)
					.intercept(MethodDelegation.to(interceptor))
					.make()
					.load(clazz.getClassLoader())
					.getLoaded()
					.getDeclaredConstructor(paramTypes)
					.newInstance(args);
		} catch (NoSuchMethodException | InvocationTargetException |InstantiationException | IllegalAccessException e) {
			throw new IllegalArgumentException(e);
		}
	}
}
