import net.clevertree.quizgate.core.BuildFingerprint;
import net.clevertree.quizgate.core.TelemetryRedaction;

public class TelemetryRedactionTest {
    public static void main(String[] args) {
        Assert.isTrue(!BuildFingerprint.TELEMETRY_UPLOAD_ENABLED, "upload default off");
        Assert.eq("QuizGateDiag", BuildFingerprint.DIAG_LOG_TAG, "diag tag");
        Assert.eq(3, BuildFingerprint.SCHEMA_VERSION, "schema");

        Assert.eq("basic-math", TelemetryRedaction.safeToken("basic-math"), "curriculum id");
        Assert.eq("m01", TelemetryRedaction.safeToken("m01"), "qid");
        Assert.eq("[redacted]", TelemetryRedaction.safeToken("kid@school.edu"), "email");
        Assert.isTrue(TelemetryRedaction.looksLikePii("555-123-4567"), "phone");
        Assert.isTrue(TelemetryRedaction.looksLikePii("192.168.1.1"), "ip");

        Assert.eq("Samsung SM-X210", TelemetryRedaction.coarseModel("Samsung", "SM-X210"), "model");
        Assert.eq("red", TelemetryRedaction.safeChoiceForUpload("red"), "color");
        Assert.eq("", TelemetryRedaction.safeChoiceForUpload("my name is Ari"), "free text");

        String msg = TelemetryRedaction.safeMessage("fail at 8.8.8.8 for a@b.co");
        Assert.isTrue(msg.contains("[ip]"), "scrub ip");
        Assert.isTrue(msg.contains("[email]"), "scrub email");

        System.out.println("TelemetryRedactionTest OK sha=" + BuildFingerprint.gitSha());
    }
}
