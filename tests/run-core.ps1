param([Parameter(Mandatory=$true)][string]$JavaHome)
$ErrorActionPreference='Stop'
$JavaHome=(Resolve-Path $JavaHome).Path
$root=Split-Path $PSScriptRoot -Parent
$out=Join-Path $root 'android\build\core-tests'
New-Item -ItemType Directory -Force $out | Out-Null
$source=Join-Path $root 'android\app\src\main\java\io\github\nannxi\epaper'
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -d $out (Join-Path $source 'ImageProcessor.java') (Join-Path $source 'Wire.java') (Join-Path $PSScriptRoot 'CoreTest.java')
if($LASTEXITCODE -ne 0){throw 'Core test compilation failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $out CoreTest
if($LASTEXITCODE -ne 0){throw 'Core tests failed'}
