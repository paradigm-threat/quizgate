import net.clevertree.quizgate.core.BuildFingerprint;

/** Compile kill-switch vs runtime remote opt-in for Intake upload. */
public class BuildFingerprintTest {
    public static void main(String[] args) {
        Assert.isTrue(!BuildFingerprint.TELEMETRY_UPLOAD_ENABLED, "compile upload off");
        Assert.isTrue(!BuildFingerprint.uploadAllowed(false), "no runtime opt-in");
        Assert.isTrue(BuildFingerprint.uploadAllowed(true), "runtime opt-in");
        System.out.println("BuildFingerprintTest OK");
    }
}
