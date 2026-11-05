# KAVACH — on-device Android phone agent (Gemma only)

Package `com.kishan.kavach` · single module · Kotlin + Compose Material3 · MVI + Clean Architecture · manual DI (`AppContainer`).

## Kya hai
Phone par chalne wala AI agent. **Gemma (LiteRT-LM) hi ekmatra LLM hai** — no cloud, no fallback model, aur `INTERNET` permission manifest me hai hi nahi. Model load na ho to saaf error dikhta hai.

## Build
1. Android Studio (Ladybug+) me folder open karo, JDK 17.
2. `./gradlew assembleDebug` (SDK path `local.properties` me).
3. App kholo → **Models** screen → apni `.litertlm` Gemma file import karo (picker se). Internet permission nahi hai, isliye download built-in nahi.
4. Accessibility service aur (optional) Notification Access settings se enable karo.

## Layers
- `domain/` tools (registry, validator, executor, guard), agent models, skills DSL, repos.
- `agent/` AgentLoop (bounded, stuck detection, cancel), planner/router, TaskManager, AutomationEngine, SkillRunner.
- `ai/` GemmaEngine, ModelManager, ToolCallParser, PromptBuilder.
- `automation/` Android tools (screen, launcher, device, productivity, comms), external intent receiver.
- `data/` Room + Prefs, `workers/` WorkManager, `services/` accessibility + notification, `presentation/` Compose screens.

## Safety
Message bhejne / data delete se pehle confirmation; payments/OTP/money apps blocked; screen content untrusted; external automation default OFF + token + allowlist + rate limit.

## Tests
`./gradlew testDebugUnitTest` ya Android SDK ke bina: `./run_core_tests.sh` (44 JVM tests). `./compile_check.sh` android.jar ke against framework code compile karta hai (stubs `tools/stubs/`).

Dekho `PROJECT_STATUS.md` (sach-sach kya verify hua) aur `CONTINUATION.md`.
