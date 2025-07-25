public class NameExpr {
    private static int sum = 0;

    public static void main(String[] args) {
        String s = new String("abc");
        int cnt = s.length();
        String ns = s + "efg";
        sum += cnt;
    }
}