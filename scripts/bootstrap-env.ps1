$ErrorActionPreference = 'Stop'
$base = 'D:\AndroidDev'
$dl   = Join-Path $base 'dl'
New-Item -ItemType Directory -Force -Path $dl | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $base 'sdk\cmdline-tools') | Out-Null

function Fetch($url, $out, $minMB) {
    if (Test-Path $out) {
        $len = (Get-Item $out).Length
        if ($len -gt ($minMB * 1MB)) { Write-Host "skip $out ($([math]::Round($len/1MB,1)) MB)"; return }
        Remove-Item $out -Force
    }
    Write-Host "downloading $url"
    & curl.exe -L --fail --retry 3 --connect-timeout 20 -o $out $url
    if ($LASTEXITCODE -ne 0) { throw "curl failed: $url" }
    Write-Host "done $(Split-Path $out -Leaf) $([math]::Round((Get-Item $out).Length/1MB,1)) MB"
}

Fetch 'https://mirrors.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_windows-x64_bin.zip' (Join-Path $dl 'jdk17.zip') 80
Fetch 'https://mirrors.huaweicloud.com/gradle/gradle-8.11.1-bin.zip' (Join-Path $dl 'gradle.zip') 50
Fetch 'https://mirrors.cloud.tencent.com/AndroidSDK/commandlinetools-win-11076708_latest.zip' (Join-Path $dl 'cmdline.zip') 50

Write-Host 'extracting jdk...'
if (-not (Test-Path (Join-Path $base 'jdk\bin\java.exe'))) {
    & tar.exe -xf (Join-Path $dl 'jdk17.zip') -C $base
    $jd = Get-ChildItem $base -Directory | Where-Object { $_.Name -like 'jdk-17*' } | Select-Object -First 1
    if ($jd) {
        if (Test-Path (Join-Path $base 'jdk')) { Remove-Item (Join-Path $base 'jdk') -Recurse -Force }
        Rename-Item $jd.FullName (Join-Path $base 'jdk')
    }
}
Write-Host 'extracting gradle...'
if (-not (Test-Path (Join-Path $base 'gradle-8.11.1\bin\gradle.bat'))) {
    & tar.exe -xf (Join-Path $dl 'gradle.zip') -C $base
}
Write-Host 'extracting cmdline-tools...'
if (-not (Test-Path (Join-Path $base 'sdk\cmdline-tools\latest\bin\sdkmanager.bat'))) {
    & tar.exe -xf (Join-Path $dl 'cmdline.zip') -C (Join-Path $base 'sdk\cmdline-tools')
    $ct = Join-Path $base 'sdk\cmdline-tools\cmdline-tools'
    if (Test-Path $ct) { Rename-Item $ct (Join-Path $base 'sdk\cmdline-tools\latest') }
}

$env:JAVA_HOME = Join-Path $base 'jdk'
$env:ANDROID_HOME = Join-Path $base 'sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

& "$env:JAVA_HOME\bin\java.exe" -version 2>&1 | Out-Host

$sdkm = Join-Path $base 'sdk\cmdline-tools\latest\bin\sdkmanager.bat'
$sdkRoot = $env:ANDROID_HOME
$yes = Join-Path $dl 'yes.txt'
if (-not (Test-Path $yes)) { ("y`n" * 200) | Set-Content -Encoding ASCII $yes }

Write-Host 'accepting licenses...'
& cmd /c "`"$sdkm`" --sdk_root=`"$sdkRoot`" --licenses < `"$yes`"" 2>&1 | Out-Host

Write-Host 'installing platform-tools / platform-35 / build-tools 35.0.0 ...'
& cmd /c "`"$sdkm`" --sdk_root=`"$sdkRoot`" `"platform-tools`" `"platforms;android-35`" `"build-tools;35.0.0`" < `"$yes`"" 2>&1 | Out-Host

Write-Host 'sdk contents:'
Get-ChildItem $sdkRoot -Directory | Select-Object -ExpandProperty Name | Out-Host
Write-Host 'BOOTSTRAP OK'
