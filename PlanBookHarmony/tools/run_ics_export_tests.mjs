// IcsExporter 单测：导出 → IcsParser.parse roundtrip 还原。
// 运行："C:/Program Files/Huawei/DevEco Studio/tools/node/node.exe" tools/run_ics_export_tests.mjs
import { mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const outDir = resolve(root, '.ts-build');
rmSync(outDir, { recursive: true, force: true });
mkdirSync(outDir, { recursive: true });

const du = readFileSync(resolve(root, 'entry/src/main/ets/common/DateUtils.ets'), 'utf8');
writeFileSync(resolve(outDir, 'DateUtils.ts'), du);
const ip = readFileSync(resolve(root, 'entry/src/main/ets/data/IcsParser.ets'), 'utf8');
writeFileSync(resolve(outDir, 'IcsParser.ts'), ip.replace(
  "import { DateUtils } from '../common/DateUtils';",
  "import { DateUtils } from './DateUtils.ts';"));
// IcsExporter 依赖 @ohos（fs/picker）——只取纯函数部分：去掉 @ohos import 与 saveToFile
let ie = readFileSync(resolve(root, 'entry/src/main/ets/data/IcsExporter.ets'), 'utf8');
ie = ie.replace(/import \{ Task \} from '..\/model\/Models';/, 'class Task {}\nconst __unused = null;');
ie = ie.replace(/import[\s\S]*?from '@kit[^']*';/g, '');
ie = ie.replace(/import fs from '@ohos.file.fs';/, '');
ie = ie.replace(/import util from '@ohos.util';/, '');
// 去掉 saveToFile（依赖 @ohos）
ie = ie.replace(/  \/\*\* 保存到用户选择的位置[\s\S]*?\n  \}\n/, '');
writeFileSync(resolve(outDir, 'IcsExporter.ts'), ie);

const { IcsParser } = await import(pathToFileURL(resolve(outDir, 'IcsParser.ts')).href);
const { IcsExporter } = await import(pathToFileURL(resolve(outDir, 'IcsExporter.ts')).href);

let passed = 0, failed = 0;
function check(name, cond, detail) {
  if (cond) { passed++; console.log(`  ok  ${name}`); }
  else { failed++; console.error(`FAIL  ${name}${detail === undefined ? '' : ' :: ' + detail}`); }
}
const eq = (a, b) => JSON.stringify(a) === JSON.stringify(b);

// 任务构造器（对应 Models.Task 字段）
function task(o) {
  return Object.assign({
    id: 1, notebookId: 1, title: '', description: '', location: null,
    type: 'ONE_OFF', startDate: '2026-09-07', endDate: '2026-09-07',
    startTime: null, endTime: null, repeatRule: null, weeklyDays: null,
    isCompleted: false, completedAt: null, isAutoReview: false, reviewType: null, createdAt: 0
  }, o);
}

// 1. 带时段任务 roundtrip（时间/地点/备注保留）
{
  const t = task({ title: '高等数学', description: '教师：张老师', location: '303教室',
    startDate: '2026-09-07', endDate: '2026-09-07', startTime: '08:30', endTime: '10:00' });
  const content = IcsExporter.buildContent([t], '我的课表');
  const back = IcsParser.parse(content);
  check('roundtrip/size', back.length === 1, `got ${back.length}`);
  check('roundtrip/fields', back[0].title === '高等数学' && back[0].startTime === '08:30' &&
    back[0].endTime === '10:00' && back[0].location === '303教室' &&
    back[0].description === '教师：张老师' && back[0].date === '2026-09-07');
}

// 2. 全天/无时段任务 → VALUE=DATE，解析后 startTime null
{
  const t = task({ title: '班会', startTime: null, endTime: null, startDate: '2026-09-10', endDate: '2026-09-10' });
  const back = IcsParser.parse(IcsExporter.buildContent([t], 'x'));
  check('allday/roundtrip', back.length === 1 && back[0].date === '2026-09-10' &&
    back[0].startTime === null && back[0].endTime === null);
}

// 3. 特殊字符转义（逗号/分号/反斜杠）roundtrip 不丢
{
  const t = task({ title: '概率论,与统计;复习\\讲义', startTime: '08:00' });
  const back = IcsParser.parse(IcsExporter.buildContent([t], 'x'));
  check('escape/roundtrip', back[0].title === '概率论,与统计;复习\\讲义', JSON.stringify(back[0].title));
}

// 4. endTime 缺失 → DTEND 沿用 startTime
{
  const t = task({ title: '课', startTime: '09:00', endTime: null });
  const back = IcsParser.parse(IcsExporter.buildContent([t], 'x'));
  check('endFallback', back[0].endTime === '09:00');
}

// 5. 整课表（20 周 × 每周一次）roundtrip 数量一致
{
  const tasks = [];
  for (let w = 1; w <= 20; w++) {
    const d = new Date(Date.UTC(2026, 7, 31) + ((w - 1) * 7) * 86400000);
    const ds = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`;
    tasks.push(task({ title: '英语', startDate: ds, endDate: ds, startTime: '10:20', endTime: '12:00', location: '外-201' }));
  }
  const back = IcsParser.parse(IcsExporter.buildContent(tasks, '英语课表'));
  check('bulk/20weeks', back.length === 20 && back.every(x => x.title === '英语' && x.startTime === '10:20'));
  check('bulk/order', back[0].date === '2026-08-31' && back[19].date === '2027-01-11');
}

console.log(`\n${passed} passed, ${failed} failed`);
process.exitCode = failed === 0 ? 0 : 1;
