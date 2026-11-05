# DRAIVO LOG

## [2025-02-23] Phase U0: SAFETY FIRST
- **Tasks**: T-U0.1 (SensitiveActionGuard/Guard update), T-U0.2 (Origin matrix / ToolPolicy), T-U0.3 (Confirmation timeout verification), T-U0.4 (Kill switch & Gesture ensureActive).
- **Status**: Completed.
- **Tags**: VERIFIED-BY-HUMAN (56 JVM tests passed).
- **Files Modified/Created**:
  - `domain/tools/Guard.kt`
  - `domain/tools/GuardTest.kt`
  - `domain/tools/ToolPolicy.kt`
  - `domain/tools/ToolPolicyTest.kt`
  - `services/accessibility/KavachAccess.kt`

## [2025-02-23] Phase U1: SEE THE SCREEN AS A SMALL OUTLINE, TAP BY ELEMENT ID
- **Tasks**: T-U1.1 (UiNode + OutlineBuilder), T-U1.2 (EidRegistry + Relocator), T-U1.3 (ScreenReader + read_screen/tap_element + flags), T-U1.4 (SensitiveActionGuard on live resolved node label).
- **Status**: Completed.
- **Tags**: VERIFIED-BY-HUMAN (69 JVM tests passed).
- **Files Modified/Created**:
  - `domain/screen/UiNode.kt` (NEW)
  - `domain/screen/OutlineBuilder.kt` (NEW)
  - `domain/screen/OutlineBuilderTest.kt` (NEW)
  - `domain/screen/EidRegistry.kt` (NEW)
  - `domain/screen/Relocator.kt` (NEW)
  - `domain/screen/RelocatorTest.kt` (NEW)
  - `automation/accessibility/ScreenReader.kt` (NEW)
  - `automation/tools/ScreenTools.kt` (MODIFIED)
  - `automation/tools/ScreenToolsTest.kt` (NEW)
  - `res/xml/accessibility_service_config.xml` (MODIFIED)

## [2025-02-23] Phase U2: ACT, THEN VERIFY WHAT HAPPENED
- **Tasks**: T-U2.1 (ToolResult sealed interface & ScreenFingerprint/ScreenDiff), T-U2.2 (awaitSettled), T-U2.3 (RepeatGuard), T-U2.4 (Action verification pattern in ScreenTools), T-U2.5 (Blind/Withheld screen detection & interactive windows flag).
- **Status**: Completed.
- **Tags**: VERIFIED-BY-HUMAN (80 JVM tests passed).
- **Files Modified/Created**:
  - `domain/tools/Tool.kt` (MODIFIED)
  - `domain/screen/ScreenFingerprint.kt` (NEW)
  - `domain/screen/ScreenFingerprintTest.kt` (NEW)
  - `domain/screen/AwaitSettled.kt` (NEW)
  - `domain/screen/AwaitSettledTest.kt` (NEW)
  - `domain/screen/RepeatGuard.kt` (NEW)
  - `domain/screen/RepeatGuardTest.kt` (NEW)
  - `automation/accessibility/ScreenReader.kt` (MODIFIED)
  - `automation/tools/ScreenTools.kt` (MODIFIED)
  - `automation/tools/LauncherTools.kt` (MODIFIED)
  - `res/xml/accessibility_service_config.xml` (MODIFIED)

## [2025-02-23] Phase U3: TYPE LIKE A KEYBOARD, VERIFY, SUBMIT THE RIGHT WAY
- **Tasks**: T-U3.1 (flagInputMethodEditor flag in config), T-U3.2 (A11yInput adapter), T-U3.3 (TypePolicy), T-U3.4 (type_text tool & sensitive field check), T-U3.5 (submit_input tool & IME action submission).
- **Status**: Completed.
- **Tags**: VERIFIED-BY-HUMAN (84 JVM tests passed).
- **Files Modified/Created**:
  - `res/xml/accessibility_service_config.xml` (MODIFIED)
  - `automation/accessibility/A11yInput.kt` (NEW)
  - `automation/accessibility/A11ySubmit.kt` (NEW)
  - `domain/screen/TypePolicy.kt` (NEW)
  - `domain/screen/TypePolicyTest.kt` (NEW)
  - `automation/tools/ScreenTools.kt` (MODIFIED)
  - `automation/tools/TypeAndSubmitTest.kt` (NEW)

## [2025-02-23] Phase U4: HONEST TOOLS + ONE HERO SKILL
- **Tasks**: T-U4.1 (PermissionGate helper), T-U4.2 (Permissionless tools: volume, brightness, etc.), T-U4.3 (Permissioned tools: contacts, calendar, send_sms with withPermission), T-U4.4 (WhatsApp hero skill & origin enforcement).
- **Status**: Completed.
- **Tags**: VERIFIED-BY-HUMAN (90 JVM tests passed).
- **Files Modified/Created**:
  - `core/permissions/PermissionGate.kt` (NEW)
  - `core/permissions/PermissionGateTest.kt` (NEW)
  - `automation/tools/DeviceTools.kt` (MODIFIED)
  - `automation/tools/CommsTools.kt` (MODIFIED)
  - `automation/tools/ProductivityTools.kt` (MODIFIED)
  - `automation/tools/HeroSkillAndPermissionTest.kt` (NEW)
  - `AndroidManifest.xml` (MODIFIED)

## [2025-02-23] Phase U5: NOTIFICATIONS THAT THE PHONE UNDERSTANDS
- **Tasks**: T-U5.1 (NotificationGate & hourly capping), T-U5.2 (TriagePrompt & TriageParser), T-U5.3 (Notification triage pipeline & Origin.NOTIFICATION constraints), T-U5.4 (NotificationListenerHealth & requestRebind).
- **Status**: Completed.
- **Tags**: VERIFIED-BY-HUMAN (99 JVM tests passed).
- **Files Modified/Created**:
  - `services/notification/NotificationGate.kt` (NEW)
  - `services/notification/NotificationGateTest.kt` (NEW)
  - `services/notification/TriagePrompt.kt` (NEW)
  - `services/notification/TriageParser.kt` (NEW)
  - `services/notification/TriageParserTest.kt` (NEW)
  - `services/notification/NotificationListenerHealth.kt` (NEW)
  - `services/notification/NotificationListenerHealthTest.kt` (NEW)
  - `services/notification/KavachNotificationService.kt` (MODIFIED)
- **Open Problems**: None.
