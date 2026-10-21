param([string]$KeilRoot = 'D:\12-keil5')
$ErrorActionPreference='Stop'
$toolDirectory=Join-Path $KeilRoot 'ARM\ARMCC\bin'
function Invoke-ArmTool([string]$Name,[string[]]$Arguments) {
    & (Join-Path $toolDirectory $Name) @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Name failed: $LASTEXITCODE" }
}
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force -Path Objects,Listings | Out-Null
    $common=@('--cpu','Cortex-M0plus','--thumb','--c99','-O1','-g','--split_sections','--library_type=microlib','-D__SAMR21G18A__','-IApp','-IDevice','-ICMSIS','-c')
    foreach($source in @('App\main.c','App\graphics.c','App\protocol.c','Drivers\epd.c','Drivers\nfc.c','Startup\system_samr21.c')) {
        $objectPath='Objects\'+[IO.Path]::GetFileNameWithoutExtension($source)+'.o'
        Invoke-ArmTool 'armcc.exe' ($common+@($source,'-o',$objectPath))
    }
    Invoke-ArmTool 'armasm.exe' @('--cpu','Cortex-M0plus','--thumb','-g','--pd','__MICROLIB SETA 1','Startup\startup_samr21.s','-o','Objects\startup_samr21.o')
    Invoke-ArmTool 'armlink.exe' @('--cpu','Cortex-M0plus','--library_type=microlib','--scatter','samr21.sct','--map','--symbols','--info','sizes','--list','Listings\epd_white.map','Objects\startup_samr21.o','Objects\system_samr21.o','Objects\main.o','Objects\graphics.o','Objects\protocol.o','Objects\epd.o','Objects\nfc.o','-o','Objects\epd_white.axf')
    Invoke-ArmTool 'fromelf.exe' @('--bin','--output','Objects\epd_white.bin','Objects\epd_white.axf')
    Invoke-ArmTool 'fromelf.exe' @('--i32','--output','Objects\epd_white.hex','Objects\epd_white.axf')
    Write-Host 'Built: Objects\epd_white.axf / .bin / .hex'
} finally { Pop-Location }
