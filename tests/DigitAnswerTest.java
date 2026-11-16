public final class DigitAnswerTest {
    public static void main(String[] args) {
        net.clevertree.quizgate.core.DigitAnswer d = new net.clevertree.quizgate.core.DigitAnswer();
        Assert.isTrue(d.isEmpty(), "starts empty");
        Assert.eq("", d.value(), "empty value");

        d.press(7);
        Assert.eq("7", d.value(), "one digit");
        Assert.isTrue(d.matches("7"), "matches 7");
        Assert.isTrue(!d.matches("8"), "not 8");

        d.press(0);
        Assert.eq("70", d.value(), "two digits");
        Assert.isTrue(!d.matches("7"), "70 != 7");

        d.press(1); // ignored — max 2
        Assert.eq("70", d.value(), "max len");

        d.clear();
        Assert.isTrue(d.isEmpty(), "cleared");

        d.press(0);
        d.press(7);
        Assert.eq("07", d.value(), "leading zero raw");
        Assert.isTrue(d.matches("7"), "07 matches 7");
        Assert.isTrue(d.matches("07"), "07 matches 07");

        d.backspace();
        Assert.eq("0", d.value(), "backspace");
        d.backspace();
        Assert.isTrue(d.isEmpty(), "backspace to empty");

        d.press(-1);
        d.press(99);
        Assert.isTrue(d.isEmpty(), "invalid digits ignored");

        System.out.println("DigitAnswerTest OK");
    }
}
