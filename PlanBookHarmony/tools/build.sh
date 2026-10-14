#!/usr/bin/env bash
# PlanBookHarmony 命令行构建脚本（Git Bash / Windows）。
# 依赖：DevEco Studio 26 安装于 "C:/Program Files/Huawei/DevEco Studio"（SDK 26 / hvigor 6.26.4 / 自带 Node 24 / 自带 JBR）。
# 产物：entry/build/default/outputs/default/entry-default-unsigned.hap（未签名；DevEco 里可配调试证书安装）
#
# 本机的坑（换机器注意）：带空格的路径经 shell→.bat→node 边界后解析不到
# （node/java 报“不是内部或外部命令”/ spawn ENOENT）。对策：给 JBR 建无空格 junction（C:/hm-jbr，
# 免管理员），并绕过 hvigorw.bat、由 run_hvigor.mjs 在 node 进程内注入 PATH 后直接驱动 hvigorw.js。
set -e

DEVECO="${DEVECO:-C:/Program Files/Huawei/DevEco Studio}"
export DEVECO
NODE="$DEVECO/tools/node/node.exe"

# 无空格 junction（已存在则跳过；junction 创建不需要管理员）
if [ ! -e "C:/hm-jbr/bin/java.exe" ]; then
  powershell -NoProfile -Command "New-Item -ItemType Junction -Path 'C:\hm-jbr' -Target '$DEVECO\jbr' | Out-Null"
fi

cd "${0%/*}/.."
"$NODE" tools/run_hvigor.mjs --mode module -p module=entry@default -p product=default assembleHap --no-daemon "$@"

echo
echo "HAP 产物：entry/build/default/outputs/default/entry-default-unsigned.hap"
