param(
    [ValidateSet('flash','status','white','black-border','white-border','pattern','red','demo','recover')]
    [string]$Action='flash'
)
$ErrorActionPreference='Stop'
$workspacePython=Join-Path $PSScriptRoot '..\..\..\work\diagnostics\venv\Scripts\python.exe'
$localPython=Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
if(Test-Path -LiteralPath $localPython) { $flashPython=$localPython }
elseif(Test-Path -LiteralPath $workspacePython) { $flashPython=$workspacePython }
else { throw 'Create .venv and install Tools\requirements.txt, or use the existing task environment.' }
if($Action -eq 'recover') {
    $runFolder=Join-Path $PSScriptRoot ('Runs\recovery_'+[guid]::NewGuid().ToString('N'))
    & $flashPython (Join-Path $PSScriptRoot 'Tools\samr21_swd_flash.py') restore-original --reset-halt --allow-nonoriginal-current --output $runFolder
} else {
    & $flashPython (Join-Path $PSScriptRoot 'Tools\flash_app.py') $Action
}
if($LASTEXITCODE -ne 0) { throw "Operation failed: $Action. Read the newest Runs\*\result.json." }
