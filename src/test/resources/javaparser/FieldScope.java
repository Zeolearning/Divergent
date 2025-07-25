public class FieldScope {
    private int field;
    private int[][] arr;
    private Class<?> classTemplate;

    public FieldScope() {
        this.field = 0;
        this.arr = new int[10][10];
    }

    public void main() {
        int x = this.field;
        int y = Test.staticField;
        var z = this.arr[0];
        var c = this.classTemplate;
    }

}

class Test {
    @Override
    public static int staticField = 0;
}