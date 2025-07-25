enum Enum {
	A {
		@Override
		public String toString() {
			return "Entry-A";
		}

	}, B {
		@Override
		public String toString() {
			return "Entry-B";
		}
	};

	@Override
	public String toString() {
		return super.toString();
	}
}
