# Pull a screenshot from device. Usage: HDC_TARGET=<t> SHOT=<name> powershell -File tools/shot-pull.ps1
$hdc = 'C:\Program Files\Huawei\DevEco Studio\sdk\default\openharmony\toolchains\hdc.exe'
$name = $env:SHOT
& $hdc -t $env:HDC_TARGET shell snapshot_display -f /data/local/tmp/$name
& $hdc -t $env:HDC_TARGET file recv "/data/local/tmp/$name" "$env:TEMP\pb-db\$name"
exit 0
