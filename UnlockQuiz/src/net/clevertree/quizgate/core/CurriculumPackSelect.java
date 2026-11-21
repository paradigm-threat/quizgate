package net.clevertree.quizgate.core;

/** Choose installed vs bundled pack without Android dependencies. */
public final class CurriculumPackSelect {
    private CurriculumPackSelect() {}

    /** Prefer higher {@link CurriculumPack#version}; equal version prefers {@code a}. */
    public static CurriculumPack preferNewer(CurriculumPack a, CurriculumPack b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.version > b.version) return a;
        if (b.version > a.version) return b;
        return a;
    }

    /**
     * Whether an index entry should be downloaded given local version/sha.
     * {@code bundled} is ignored — presence of packUrl + newer version/sha decides.
     */
    public static boolean needsInstall(int indexVersion, String indexSha,
                                       int localVersion, String localSha,
                                       String packUrl) {
        if (packUrl == null || packUrl.isEmpty()) return false;
        if (indexVersion > localVersion) return true;
        if (indexVersion < localVersion) return false;
        if (indexSha == null || indexSha.isEmpty()) return false;
        String ls = localSha == null ? "" : localSha;
        return ls.isEmpty() || !indexSha.equalsIgnoreCase(ls);
    }
}
