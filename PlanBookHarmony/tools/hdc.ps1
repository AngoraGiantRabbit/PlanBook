# hdc wrapper for restricted shells: passes all args verbatim to hdc.exe.
# Usage: powershell -ExecutionPolicy Bypass -File tools/hdc.ps1 <hdc args...>
& 'C:\Program Files\Huawei\DevEco Studio\sdk\default\openharmony\toolchains\hdc.exe' @args
exit $LASTEXITCODE
