# Pull the app RDB (db+wal+shm trio) without path-arg mangling.
# Usage: HDC_TARGET=<key> LOCALDIR=<windows dir> powershell -ExecutionPolicy Bypass -File tools/hdc-db-pull.ps1
$hdc = 'C:\Program Files\Huawei\DevEco Studio\sdk\default\openharmony\toolchains\hdc.exe'
$dir = ($env:LOCALDIR -replace '/', '\')
$remote = '/data/app/el2/100/database/com.example.planbook/entry/rdb'
& $hdc -t $env:HDC_TARGET file recv "$remote/planbook.db" "$dir\planbook.db"
& $hdc -t $env:HDC_TARGET file recv "$remote/planbook.db-wal" "$dir\planbook.db-wal"
& $hdc -t $env:HDC_TARGET file recv "$remote/planbook.db-shm" "$dir\planbook.db-shm"
exit 0
