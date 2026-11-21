import net.clevertree.quizgate.core.Catalog;
import net.clevertree.quizgate.core.Question;

import java.nio.file.Files;
import java.nio.file.Paths;

public class CatalogTest {
    public static void main(String[] args) throws Exception {
        String json = new String(Files.readAllBytes(Paths.get(args[0])));
        Catalog c = Catalog.fromJson(json);
        Assert.eq(20, c.size(), "catalog size (20 math only)");
        for (Question q : c.all()) {
            q.validate();
            Assert.isTrue(q.choices.contains(q.correct), q.id + " correct in choices");
            Assert.isTrue(q.choices.size() == 3 || q.choices.size() == 4,
                    q.id + " choices 3|4 got=" + q.choices.size());
            Assert.eq("math", q.type, q.id + " math only");
            Assert.isTrue(!"color".equals(q.type), q.id + " no color");
            Assert.isTrue(!"animal".equals(q.type), q.id + " no animals");
            Assert.isTrue(!"count".equals(q.type) && !"counting".equals(q.type), q.id + " no counting");
            Assert.isTrue(!q.id.startsWith("c0"), q.id + " no counting ids");
            Assert.isTrue(!q.id.startsWith("a0"), q.id + " no animal ids");
            Assert.isTrue(!q.id.startsWith("col"), q.id + " no color ids");
            Assert.isTrue(q.symbolic.contains("+"), q.id + " symbolic");
            Assert.isTrue(q.spoken.toLowerCase().contains("plus"), q.id + " spoken plus");
        }
        Assert.isTrue(c.byId("c01") == null, "counting not in catalog");
        Assert.isTrue(c.byId("a01") == null, "animals not in catalog");
        Assert.isTrue(c.byId("col01") == null, "colors not in catalog");
        Assert.notNull(c.byId("m17"), "m17 present");
        System.out.println("CatalogTest OK");
    }
}
