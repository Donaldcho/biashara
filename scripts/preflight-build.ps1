# BiasharaAI build environment checks. Run from repo root: .\scripts\preflight-build.ps1
# Writes build-environment.log and prints the same summary to the console.

$ErrorActionPreference = "Continue"
$repoRoot = Split-Path -Parent $PSScriptRoot
$logPath = Join-Path $repoRoot "build-environment.log"
$jbr = Join-Path $env:ProgramFiles "Android\Android Studio\jbr"
$javac = Join-Path $jbr "bin\javac.exe"
$java = Join-Path $jbr "bin\java.exe"
$smokeSrc = Join-Path $PSScriptRoot "SslSmokeTest.java"
$outDir = Join-Path $env:TEMP "biasharaai-ssl-smoke"

function Write-Log {
    param([string]$msg)
    $line = "$(Get-Date -Format o) $msg"
    Add-Content -Path $logPath -Value $line
    Write-Output $line
}

Set-Content -Path $logPath -Value "BiasharaAI build preflight $(Get-Date -Format o)`n"

Write-Log "CHECK: Android Studio JBR at $jbr"
if (-not (Test-Path $java)) {
    Write-Log "FAIL: JBR java.exe not found. Install Android Studio or set BIASHARAAI_USE_SYSTEM_JAVA=1 and point JAVA_HOME to a full JDK."
    Write-Log "ACTION: Install Android Studio (includes JBR), then re-run this script."
    exit 2
}
Write-Log "OK: JBR java present."

Write-Log "CHECK: HTTPS to dl.google.com (PowerShell / Schannel)"
$psTlsOk = $false
try {
    $r = Invoke-WebRequest -Uri "https://dl.google.com/dl/android/maven2/master-index.xml" -UseBasicParsing -TimeoutSec 25
    Write-Log "OK: PowerShell HTTPS status $($r.StatusCode)."
    $psTlsOk = $true
}
catch {
    Write-Log "FAIL: PowerShell could not reach dl.google.com. $($_.Exception.Message)"
    Write-Log "ACTION: Fix network, proxy, or firewall before Gradle can download Android artifacts."
    exit 3
}

if (-not $psTlsOk) {
    exit 3
}

Write-Log "CHECK: HTTPS to dl.google.com (Java, same trust store behavior as Gradle)"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$compileOut = & $javac -encoding UTF-8 $smokeSrc -d $outDir 2>&1
if ($compileOut) {
    $compileOut | ForEach-Object { Write-Log "javac: $_" }
}

$javaOut = Join-Path $outDir "ssl-smoke-out.txt"
$javaErr = Join-Path $outDir "ssl-smoke-err.txt"
$p = Start-Process -FilePath $java -ArgumentList @("-cp", $outDir, "SslSmokeTest") -NoNewWindow -Wait -PassThru `
    -RedirectStandardOutput $javaOut -RedirectStandardError $javaErr
if (Test-Path $javaOut) {
    Get-Content $javaOut | ForEach-Object { Write-Log "java: $_" }
}
if ($p.ExitCode -ne 0) {
    if (Test-Path $javaErr) {
        Get-Content $javaErr | ForEach-Object { Write-Log "java-err: $_" }
    }
    Write-Log "FAIL: Java TLS to dl.google.com failed (exit $($p.ExitCode)). Typical cause: TLS inspection - cert trusted by Windows but missing from JDK cacerts."
    Write-Log "ACTION (pick one):"
    Write-Log "  A) Import org root .cer into JDK cacerts used by Gradle (often Android Studio JBR), e.g.:"
    $keytool = Join-Path $jbr "bin\keytool.exe"
    $cacerts = Join-Path $jbr "lib\security\cacerts"
    Write-Log ('    keytool example: "{0}" -importcert -noprompt -keystore "{1}" -storepass changeit -alias corp-tls-root -file path\to\corp-root.cer' -f $keytool, $cacerts)
    Write-Log "  B) IT allowlist: dl.google.com, repo.maven.apache.org, plugins.gradle.org, services.gradle.org"
    Write-Log "  C) After trust is fixed, use Android Studio Sync or gradlew. Use BIASHARAAI_USE_SYSTEM_JAVA=1 only to force your own JDK for Gradle."
    Write-Log "NOTE: Gradle wrapper is 8.14.3; uses local Gradle cache under USERPROFILE\.gradle when present."
    exit 1
}

Write-Log "OK: Java TLS smoke test passed."
Write-Log "DONE. Run: .\gradlew.bat assembleDebug"
exit 0
