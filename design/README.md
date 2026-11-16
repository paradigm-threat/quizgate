# QuizGate design assets

`android_icon_kit/` is the unpacked **QuizGate_Android_Complete_Kit**.

- **In the APK (launcher):** concept **A** `A_gate_question` → `UnlockQuiz/res` mipmaps + `ic_stat_quizgate` + splash/TV banner.
- **Also in the APK (named extras):** B/C splash, symbols, and feature art as `drawable-nodpi/quizgate_concept_*` / `quizgate_feature_*`.
- To switch the launcher to B or C later, replace `UnlockQuiz/res` mipmap/drawable icon files from that concept’s `android_res/` (do not merge two sets with the same resource names).
