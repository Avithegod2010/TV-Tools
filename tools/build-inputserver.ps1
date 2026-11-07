# Builds app/src/main/assets/tvtools-input.jar from the Java source.
#
# Requirements:
#   JAVA_HOME    -> JDK 17
#   ANDROID_HOME -> Android SDK with platform 35 and build-tools
#
# The jar is tiny (~2 KB) and is pushed to the TV at runtime. It is not part of
# the app dex because it has to run under the TV's shell `app_process`, not as
# an installed app.

$ErrorActionPreference = "Stop"

$root = Split-Path $PSScriptRoot -Parent
$javaHome = $env:JAVA_HOME
if (-not $javaHome) { throw "Set JAVA_HOME to JDK 17" }
$sdk = $env:ANDROID_HOME
if (-not $sdk) { throw "Set ANDROID_HOME to the Android SDK" }

$androidJar = Join-Path $sdk "platforms\android-35\android.jar"
$buildTools = Get-ChildItem (Join-Path $sdk "build-tools") -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
$d8 = Join-Path $buildTools.FullName "d8.bat"
$javac = Join-Path $javaHome "bin\javac.exe"
$jarTool = Join-Path $javaHome "bin\jar.exe"

$work = Join-Path $env:TEMP "tvtools-inputserver-build"
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path "$work\classes", "$work\dex" -Force | Out-Null

$source = Join-Path $root "tools\inputserver\com\tvtools\app\input\InputServer.java"
& $javac -source 8 -target 8 -classpath $androidJar -d "$work\classes" $source
& $d8 --lib $androidJar --min-api 26 --output "$work\dex" `
    "$work\classes\com\tvtools\app\input\InputServer.class"

$output = Join-Path $root "app\src\main\assets\tvtools-input.jar"
& $jarTool cf $output -C "$work\dex" classes.dex

$size = (Get-Item $output).Length
Write-Host "Built $output ($size bytes)"
