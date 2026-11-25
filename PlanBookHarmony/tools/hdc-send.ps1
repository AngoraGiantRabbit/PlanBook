# Send a local file to the device Download dir without path-arg mangling.
# Usage: HDC_TARGET=<key> LOCAL=<windows file> REMOTE=<device path> powershell -ExecutionPolicy Bypass -File tools/hdc-send.ps1
$hdc = 'C:\Program Files\Huawei\DevEco Studio\sdk\default\openharmony\toolchains\hdc.exe'
$local = ($env:LOCAL -replace '/', '\')
& $hdc -t $env:HDC_TARGET file send $local $env:REMOTE
exit $LASTEXITCODE
