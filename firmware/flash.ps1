param(
    [ValidateSet('flash','status','white','black-border','white-border','pattern','red','demo','recover')]
    [string]$Action='flash',
    [string]$PythonExe
)
$ErrorActionPreference='Stop'
$localPython=Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
if($PythonExe) { $flashPython=(Resolve-Path -LiteralPath $PythonExe).Path }
elseif(Test-Path -LiteralPath $localPython) { $flashPython=$localPython }
else { throw 'Create .venv and install Tools\requirements.txt, or specify -PythonExe.' }
if($Action -eq 'recover') {
    $runFolder=Join-Path $PSScriptRoot ('Runs\recovery_'+[guid]::NewGuid().ToString('N'))
    & $flashPython (Join-Path $PSScriptRoot 'Tools\samr21_swd_flash.py') restore-original --reset-halt --allow-nonoriginal-current --output $runFolder
} else {
    & $flashPython (Join-Path $PSScriptRoot 'Tools\flash_app.py') $Action
}
if($LASTEXITCODE -ne 0) { throw "Operation failed: $Action. Read the newest Runs\*\result.json." }
