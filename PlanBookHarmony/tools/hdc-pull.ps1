# Pull a file from device without passing paths as command args (restricted shells mangle them).
# hdc treats forward-slash Windows paths as relative → convert to backslashes.
# Usage: HDC_TARGET=<key> REMOTE=<device path> LOCAL=<windows path> powershell -ExecutionPolicy Bypass -File tools/hdc-pull.ps1
$hdc = 'C:\Program Files\Huawei\DevEco Studio\sdk\default\openharmony\toolchains\hdc.exe'
$local = $env:LOCAL -replace '/', '\'
& $hdc -t $env:HDC_TARGET file recv $env:REMOTE $local
exit $LASTEXITCODE
