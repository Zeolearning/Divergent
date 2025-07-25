
public class StaticMethodCall {

    public static void main(String[] args) {
        A.test(new Info(null, null));
    }
}

class A {
    public static void test(Info x) {

    }
}