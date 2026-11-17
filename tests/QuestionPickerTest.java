import net.clevertree.quizgate.core.Catalog;
import net.clevertree.quizgate.core.PresentedQuestion;
import net.clevertree.quizgate.core.Question;
import net.clevertree.quizgate.core.QuestionPicker;
import net.clevertree.quizgate.core.RenderFormat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class QuestionPickerTest {
    public static void main(String[] args) {
        Question heavy = q("add", 4);
        Question light = q("sub", 1);
        Catalog catalog = new Catalog(Arrays.asList(heavy, light));
        QuestionPicker picker = new QuestionPicker(catalog, new Random(42L));

        Map<String, Integer> counts = new HashMap<>();
        int n = 2000;
        for (int i = 0; i < n; i++) {
            String id = picker.first().id();
            counts.put(id, counts.getOrDefault(id, 0) + 1);
        }
        int add = counts.getOrDefault("add", 0);
        int sub = counts.getOrDefault("sub", 0);
        Assert.isTrue(add > sub, "heavier weight should win more often add=" + add + " sub=" + sub);
        // Expected ~80% / 20% with weights 4:1 — allow wide band for RNG
        Assert.isTrue(add > n * 0.65, "add share too low: " + add);
        Assert.isTrue(sub < n * 0.35, "sub share too high: " + sub);
        Assert.isTrue(sub > n * 0.05, "sub should still appear: " + sub);

        PresentedQuestion next = picker.nextDifferent("add");
        Assert.eq("sub", next.id(), "only other question");

        // force_qid path stays on equation (ADB / screenshots)
        QuestionPicker forced = new QuestionPicker(catalog, new Random(1L)).preferFirst("add");
        for (int i = 0; i < 20; i++) {
            Assert.eq(RenderFormat.EQUATION, forced.first().format, "preferFirst → equation");
        }

        System.out.println("QuestionPickerTest OK add=" + add + " sub=" + sub);
    }

    private static Question q(String id, int weight) {
        List<String> choices = Arrays.asList("1", "2", "3");
        return new Question(id, "math", "spoken " + id, "1+1", choices, "2", null, weight);
    }
}
