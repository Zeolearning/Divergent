package testresc.override;

import testresc.override.crosspackage.TestCrossPackage;

public class TestOverride {

	class CA {
		public void test() {

		}
	}

	class CB extends CA {
		@Override
		public void test() {
			System.out.println("Method<CB: test()>");
		}
	}

	interface IA {
		default void test_iface() {

		}

	}

	interface IB extends IA {
		@Override
		default void test_iface() {
			System.out.println("Method<IB: test_iface()>");
		}
	}


	class CC extends CB implements IB {
		@Override
		public void test() {
			System.out.println("Method<CC: test()>");
		}

		@Override
		public void test_iface() {
			System.out.println("Method<CC: test_iface()>");
		}
	}

	class CD extends TestCrossPackage {

		void test_cross_1() {

		}

		@Override
		protected void test_cross_2() {
			System.out.println("Method<CD: test_cross_2()>");
		}
	}

	enum EA {
		EA_1 {
			@Override
			public void test_iface() {
				super.test_iface();
			}
		}, EA_2 {
			@Override
			public void test_iface() {
				super.test_iface();
			}
		};

		public void test_iface() {

		}
	}
}
