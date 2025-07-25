import java.util.List;

public class Parameter {
	public int test(List<Integer> a, int l, int r) {
		// Calculate sum in arr[l:r)
		int sum = 0;
		for (int i = l; i < r; ++i) {
			sum += a.get(i);
		}
		return sum;
	}
}