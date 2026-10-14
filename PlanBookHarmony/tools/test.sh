#!/usr/bin/env bash
# IcsParser 单测（19 例，移植自 Android 版 IcsParserTest.kt）。
# 用 DevEco 自带 Node 24 的原生 TS 类型剥离直接运行 .ets 源（无三方依赖）。
set -e
DEVECO="${DEVECO:-C:/Program Files/Huawei/DevEco Studio}"
"$DEVECO/tools/node/node.exe" "$(dirname "$0")/run_ics_tests.mjs"
