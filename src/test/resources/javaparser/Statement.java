
public class Statement {

	/**
	 * Binary search
	 * @param args
	 */
	public static void main(String[] args) {
		int[] arr = new int[10];
		int l = 0, r = arr.length - 1;
		int k = 10;
		while (l <= r) {
			int mid = (l + r) / 2;
			if (arr[mid] == k) {
				break;
			} else if (arr[mid] < k) {
				l = mid + 1;
			} else {
				r = mid - 1;
			}
		}
	}
}