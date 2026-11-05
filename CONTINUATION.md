# CONTINUATION
1. Open in Android Studio, sync, fix compile errors (most likely in `presentation/` and `core/ui/`, then Room/KSP, litertlm API names in `ai/gemma/GemmaEngine.kt`).
2. Run `./gradlew testDebugUnitTest` (CoreTest*.kt, JUnit + runBlocking only).
3. Install on arm64 phone, import Gemma `.litertlm`, test: chat, instant rules (alarm/timer), skills, confirmation dialog, stop button.
4. Test accessibility tools (tap/scroll/input/screenshot), notification summary, WhatsApp draft→confirm→send.
5. Enable external automation in Settings, test RUN_TASK from Tasker with token.
6. Add: ViewModel unit tests, instrumented tests, x86_64 ABI for emulator, release signing.
Don't restart from scratch; layers are independent.
