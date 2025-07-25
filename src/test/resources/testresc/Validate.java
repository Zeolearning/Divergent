package testresc;

import testresc.imports.TestEnum;
import testresc.imports.TestForImport;
import testresc.imports.TestForImport.*;

import static testresc.imports.TestEnum.*;

import java.util.List;

public class Validate {

	public static void main(String[] args) {
		TestForImport testForImport = new TestForImport();
		InnerB innerB = testForImport.new InnerB();
		TestForImport.InnerB innerB1;
		List<Integer> L;
	}
}
