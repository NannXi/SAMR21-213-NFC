param(
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$AndroidSdk,
    [string]$BuildToolsVersion='35.0.0',
    [string]$KeyStore
)
$ErrorActionPreference='Stop'
$JavaHome=(Resolve-Path $JavaHome).Path
$AndroidSdk=(Resolve-Path $AndroidSdk).Path
$tools=Join-Path $AndroidSdk "build-tools\$BuildToolsVersion"
if(!(Test-Path $tools)){throw "Build tools missing: $tools"}
$jar=Join-Path $AndroidSdk 'platforms\android-35\android.jar'
if(!(Test-Path $jar)){throw 'Install Android SDK Platform 35'}
$env:JAVA_HOME=$JavaHome
$env:PATH=(Join-Path $JavaHome 'bin')+';'+$env:PATH
$build=Join-Path $PSScriptRoot 'build'
$release=Join-Path $PSScriptRoot '..\releases'
New-Item -ItemType Directory -Force $build,$release,(Join-Path $build 'classes'),(Join-Path $build 'dex') | Out-Null
function Run([string]$program,[string[]]$toolArgs){& $program @toolArgs;if($LASTEXITCODE -ne 0){throw "$program failed ($LASTEXITCODE)"}}
$sources=@(Get-ChildItem (Join-Path $PSScriptRoot 'app\src\main\java') -Filter *.java -Recurse | ForEach-Object FullName)
Run (Join-Path $JavaHome 'bin\javac.exe') (@('-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-classpath',$jar,'-d',(Join-Path $build 'classes'))+$sources)
$classes=@(Get-ChildItem (Join-Path $build 'classes') -Filter *.class -Recurse | ForEach-Object FullName)
Run (Join-Path $tools 'd8.bat') (@('--lib',$jar,'--min-api','26','--output',(Join-Path $build 'dex'))+$classes)
$resourceApk=Join-Path $build 'resources.apk'
Run (Join-Path $tools 'aapt2.exe') @('link','-o',$resourceApk,'-I',$jar,'--manifest',(Join-Path $PSScriptRoot 'app\src\main\AndroidManifest.xml'))
Run (Join-Path $JavaHome 'bin\jar.exe') @('uf',$resourceApk,'-C',(Join-Path $build 'dex'),'classes.dex')
$aligned=Join-Path $build 'aligned.apk'
Run (Join-Path $tools 'zipalign.exe') @('-f','4',$resourceApk,$aligned)
if(!$KeyStore){$KeyStore=Join-Path $build 'android-debug.keystore'}
if(!(Test-Path $KeyStore)){
    Run (Join-Path $JavaHome 'bin\keytool.exe') @('-genkeypair','-keystore',$KeyStore,'-storepass','android','-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Android Debug,O=Android,C=US')
}
$apk=Join-Path $release 'epaper-nfc-v0.1.1-debug.apk'
Run (Join-Path $tools 'apksigner.bat') @('sign','--ks',$KeyStore,'--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,$aligned)
Run (Join-Path $tools 'apksigner.bat') @('verify','--verbose',$apk)
Get-FileHash $apk -Algorithm SHA256
Write-Host "Built: $apk"
