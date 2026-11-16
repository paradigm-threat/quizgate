public final class Assert {
    private Assert() {}

    public static void eq(Object exp, Object act, String msg) {
        if (exp == null ? act != null : !exp.equals(act)) {
            throw new AssertionError(msg + " expected=" + exp + " actual=" + act);
        }
    }

    public static void eq(int exp, int act, String msg) {
        if (exp != act) throw new AssertionError(msg + " expected=" + exp + " actual=" + act);
    }

    public static void eq(long exp, long act, String msg) {
        if (exp != act) throw new AssertionError(msg + " expected=" + exp + " actual=" + act);
    }

    public static void eq(double exp, double act, double eps, String msg) {
        if (Math.abs(exp - act) > eps) {
            throw new AssertionError(msg + " expected=" + exp + " actual=" + act);
        }
    }

    public static void isTrue(boolean v, String msg) {
        if (!v) throw new AssertionError(msg);
    }

    public static void notNull(Object o, String msg) {
        if (o == null) throw new AssertionError(msg);
    }
}
