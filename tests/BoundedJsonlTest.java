import net.clevertree.quizgate.core.BoundedJsonl;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class BoundedJsonlTest {
    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("qg-bounded").toFile();
        File f = new File(dir, "ring.jsonl");
        BoundedJsonl log = new BoundedJsonl(f, 10, 400, 3);

        for (int i = 0; i < 25; i++) {
            log.append("{\"n\":" + i + ",\"pad\":\"xxxxxxxx\"}");
        }
        log.compact();
        List<String> kept = log.readAll();
        Assert.isTrue(kept.size() <= 10, "line cap <= 10 got=" + kept.size());
        Assert.isTrue(log.sizeBytes() <= 400, "byte cap <= 400 got=" + log.sizeBytes());
        // Newest retained
        Assert.isTrue(kept.get(kept.size() - 1).contains("\"n\":24"), "newest is 24");

        List<String> sample = new ArrayList<>();
        for (int i = 0; i < 20; i++) sample.add("{\"x\":" + i + "}");
        List<String> fit = BoundedJsonl.selectNewestFitting(sample, 5, 10_000);
        Assert.eq(5, fit.size(), "selectNewestFitting lines");
        Assert.isTrue(fit.get(0).contains("\"x\":15"), "oldest kept is 15");

        List<String> tiny = BoundedJsonl.selectNewestFitting(sample, 20, 30);
        Assert.isTrue(tiny.size() < sample.size(), "byte budget shrinks");
        Assert.isTrue(tiny.get(tiny.size() - 1).contains("\"x\":19"), "still newest last");

        System.out.println("BoundedJsonlTest OK lines=" + kept.size() + " bytes=" + log.sizeBytes());
    }
}
