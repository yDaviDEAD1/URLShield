# Gera o APK de depuração usando o JDK, o Android SDK e o Gradle locais da pasta "work".
#   .\build-apk.ps1                                   -> somente análise offline
#   .\build-apk.ps1 -ApiUrl https://sua-api.onrender.com  -> offline + servidor na nuvem
#   .\build-apk.ps1 -ApiUrl http://10.0.2.2:8000          -> emulador com API local
param(
    [string]$ApiUrl = ""
)

$ErrorActionPreference = "Stop"
$work = Resolve-Path (Join-Path $PSScriptRoot "..\..")

$env:JAVA_HOME = (Get-ChildItem (Join-Path $work "jdk17") -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = Join-Path $work "android-sdk"
$env:GRADLE_USER_HOME = Join-Path $work "gradle-home"
$env:ANDROID_USER_HOME = Join-Path $PSScriptRoot "..\android-user-home"

Push-Location $PSScriptRoot
try {
    # Avisos do javac saem no stderr; no PowerShell 5.1 não podem interromper o build
    $ErrorActionPreference = "Continue"
    & .\gradlew.bat assembleDebug --console=plain "-PURL_SHIELD_API_URL=$ApiUrl" 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "Falha no build do APK." }
    Write-Host "APK gerado em: $PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
} finally {
    Pop-Location
}
