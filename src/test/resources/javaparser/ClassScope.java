import com.github.divergent.model.Graph;
import com.github.divergent.utils.StringReps;

public class ClassScope {

	public ClassScope() {
		A.B.C obj = new A.B.C();
		X x = new X();
		Y y = x.new Y();
		new Graph<Integer, Long>();
	}

	public static class A {
		public static class B {
			public static class C {
				public void testC() {

				}
			}
		}
	}

	public class X {
		public class Y {

		}
	}
}