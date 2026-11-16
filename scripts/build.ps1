# 构建日计 APK（Windows / 纯命令行，不需要 Android Studio）
# 用法：powershell -ExecutionPolicy Bypass -File scripts\build.ps1
$ErrorActionPreference = 'Stop'

$env:JAVA_HOME = 'D:\AndroidDev\jdk'
$env:ANDROID_HOME = 'D:\AndroidDev\sdk'
$env:ANDROID_SDK_ROOT = 'D:\AndroidDev\sdk'
$env:PATH = "$env:JAVA_HOME\bin;D:\AndroidDev\gradle-8.11.1\bin;$env:PATH"

$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ascii = 'D:\DayLedger'

# AGP 不接受非 ASCII 路径；用目录联接把工程映射到 D:\
if (-not (Test-Path $ascii)) {
    cmd /c mklink /J "$ascii" "$project" | Out-Null
}

& 'D:\AndroidDev\gradle-8.11.1\bin\gradle.bat' -p $ascii :app:assembleDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw "构建失败" }

$apk = Join-Path $ascii 'app\build\outputs\apk\debug\app-debug.apk'
$dist = Join-Path $project 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
Copy-Item $apk (Join-Path $dist 'dayledger-0.1.0-debug.apk') -Force
Write-Host "APK: $dist\dayledger-0.1.0-debug.apk"
