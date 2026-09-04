# Baseline Verification

Branch: `codex/small-store-growth-foundation`

Verification date: 2026-09-04

This record describes the baseline before the next growth implementation. It is not a substitute for continuous integration.

## Desktop Application

Command:

```powershell
.\gradlew.bat -p desktop-standalone clean test installDist --offline --no-daemon --console=plain
```

Result: passed. Seven tests completed with zero failures. The standalone distribution was rebuilt.

## Windows Shell

Command:

```powershell
dotnet build desktop-shell-windows\BiasharaDesktopShell.csproj -c Release --no-restore
```

Result: passed with zero errors. The installed .NET 10 SDK reported an existing `WindowsBase` reference-version warning involving WebView2.

## Android Application

The Android production source passed KSP and Kotlin/Java compilation during:

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon --console=plain
```

The unit-test task itself did not start. Gradle could not download the declared test-only artifacts for MockK 1.13.13, Turbine 1.2.0, kotlinx-coroutines-test 1.10.1, and Byte Buddy because Java rejected the Maven Central certificate chain with `PKIX path building failed`.

The failure reproduced under Android Studio JBR 21 and Temurin 17. This is a host trust-store/dependency-cache blocker, not a reported test assertion or production compilation failure. CI must run the complete Android test suite from a network environment with a valid Maven Central trust chain before release.

## Static Checks

- `git diff --check`: no whitespace errors.
- Credential-pattern scan over source: no credential-like values found.
- Generated build directories, local assistant metadata, signing material, model files, and unrelated workspace documents are ignored.

## Growth Increment Verification

The signed phone-sync increment was verified on the same branch after the baseline checkpoint.

Desktop command:

```powershell
.\gradlew.bat -p desktop-standalone clean test installDist --offline --no-daemon --console=plain
```

Result: passed. Seventeen tests completed with zero failures, including canonical request signing, body tamper rejection, clock expiry, replay rejection, signed-only downgrade rejection, pairing throttling, and legacy bridge-session loading. The standalone JAR and install distribution were rebuilt.

Android command:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Result: passed. Kotlin, Java, Hilt, DEX, packaging, and debug APK assembly completed with the shared `sync-contract` module.

Live bridge smoke test:

- Protocol capabilities returned `1.0` and `HMAC-SHA256`.
- A capable mobile pairing selected protocol `1.0`.
- A correctly signed scan was accepted.
- Repeating the identical signed request returned HTTP `409`.
