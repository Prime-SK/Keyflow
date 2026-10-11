param([switch]$Install, [switch]$Standalone)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle'

# Use workspace tools when present, otherwise an Android Studio/JDK installation.
$localJdkRoot = Join-Path $projectRoot '.tools\jdk17'
if (Test-Path -LiteralPath $localJdkRoot) {
  $taskJdk = Get-ChildItem -LiteralPath $localJdkRoot -Directory | Select-Object -First 1
  if ($taskJdk) { $env:JAVA_HOME = $taskJdk.FullName }
}
$localSdkRoot = Join-Path $projectRoot '.tools\android-sdk'
if (Test-Path -LiteralPath (Join-Path $localSdkRoot 'platform-tools')) {
  $env:ANDROID_HOME = $localSdkRoot
  $env:ANDROID_SDK_ROOT = $localSdkRoot
}
if (-not $env:JAVA_HOME -or -not $env:ANDROID_HOME) {
  throw 'Set JAVA_HOME to JDK 17 and ANDROID_HOME to an Android SDK installation. See docs/phase-1.md.'
}

& npx.cmd expo prebuild --platform android --no-install
if ($LASTEXITCODE -ne 0) { throw 'Android project generation failed.' }
$sdkPathForGradle = $env:ANDROID_HOME.Replace('\', '/')
Set-Content -LiteralPath (Join-Path $projectRoot 'android\local.properties') -Value "sdk.dir=$sdkPathForGradle" -Encoding ascii

Push-Location -LiteralPath (Join-Path $projectRoot 'android')
$variant = if ($Standalone) { 'release' } else { 'debug' }
$env:NODE_ENV = if ($Standalone) { 'production' } else { 'development' }
$assembleTask = if ($Standalone) { ':app:assembleRelease' } else { ':app:assembleDebug' }
try {
  & .\gradlew.bat :keyflow-playback:testDebugUnitTest :keyflow-library:testDebugUnitTest $assembleTask '-PreactNativeArchitectures=arm64-v8a' --no-daemon --no-watch-fs --max-workers=4 --console=plain
  if ($LASTEXITCODE -ne 0) { throw 'Android build or native unit tests failed.' }
} finally { Pop-Location }

$artifactsRoot = Join-Path $projectRoot 'artifacts'
New-Item -ItemType Directory -Path $artifactsRoot -Force | Out-Null
$apkName = if ($Standalone) { 'keyflow-test.apk' } else { 'keyflow-development.apk' }
$apkPath = Join-Path $artifactsRoot $apkName
Copy-Item -LiteralPath (Join-Path $projectRoot "android\app\build\outputs\apk\$variant\app-$variant.apk") -Destination $apkPath
Write-Output "Test APK: $apkPath"
if ($Install) {
  & (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe') install -r $apkPath
  if ($LASTEXITCODE -ne 0) { throw 'APK installation failed. Connect an authorized Android device with USB debugging enabled.' }
}
