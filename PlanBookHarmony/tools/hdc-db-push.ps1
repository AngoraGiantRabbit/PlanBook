# Push the app RDB back and clear stale wal/shm on device (HANDOVER §7.3).
# Usage: HDC_TARGET=<key> LOCALDIR=<windows dir> powershell -ExecutionPolicy Bypass -File tools/hdc-db-push.ps1
$hdc = 'C:\Program Files\Huawei\DevEco Studio\sdk\default\openharmony\toolchains\hdc.exe'
$dir = ($env:LOCALDIR -replace '/', '\')
$remote = '/data/app/el2/100/database/com.example.planbook/entry/rdb'
& $hdc -t $env:HDC_TARGET file send "$dir\planbook.db" "$remote/planbook.db"
& $hdc -t $env:HDC_TARGET shell "rm -f $remote/planbook.db-wal $remote/planbook.db-shm"
exit 0
