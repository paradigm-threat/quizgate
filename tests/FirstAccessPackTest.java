import net.clevertree.quizgate.core.Catalog;
import net.clevertree.quizgate.core.CurriculumPack;
import net.clevertree.quizgate.core.CurriculumParser;
import net.clevertree.quizgate.core.Question;
import net.clevertree.quizgate.core.RenderFormat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FirstAccessPackTest {
    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length > 0 ? args[0] : "UnlockQuiz/assets/curricula/first-access");
        String manifest = Files.readString(root.resolve("manifest.json"));
        String curriculum = Files.readString(root.resolve("curriculum.json"));
        CurriculumPack pack = CurriculumParser.parse(manifest, curriculum);
        Assert.eq("first-access", pack.id, "pack id");
        Assert.isTrue(pack.catalog.size() >= 40, "enough items");
        Assert.eq(1, pack.roundSize, "round size");

        Catalog l0 = pack.catalogFiltered("all", 0);
        Assert.isTrue(l0.size() >= 10, "L0 pool");
        boolean sawLetter = false;
        boolean sawReverse = false;
        boolean sawAccess = false;
        for (Question q : l0.all()) {
            Assert.eq("access", q.type, "type access");
            sawAccess = true;
            if (q.correct.length() == 1 && Character.isLetter(q.correct.charAt(0))) {
                sawLetter = true;
            }
            if (q.spoken != null && q.spoken.contains("down")) {
                sawReverse = true;
            }
            Assert.isTrue(RenderFormat.allowedFor(q).size() >= 1, "formats");
        }
        Assert.isTrue(sawAccess, "access items");
        Assert.isTrue(sawLetter, "L0 has letters");
        Assert.isTrue(sawReverse, "L0 has reverse");

        Catalog l3 = pack.catalogFiltered("all", 3);
        Assert.isTrue(l3.size() > l0.size(), "L3 includes lower levels");
        System.out.println("FirstAccessPackTest OK items=" + pack.catalog.size()
                + " L0=" + l0.size() + " L3=" + l3.size());
    }
}
