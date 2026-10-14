// IcsParser 移植验证：把 .ets 源拷成 .ts，用 Node 24 原生类型剥离直接运行。
// 测试用例 = Android 版 IcsParserTest.kt 的 19 例忠实移植。
// 基准事实：2026-09-07 是周一，2026-09-09 是周三。
// 运行："C:/Program Files/Huawei/DevEco Studio/tools/node/node.exe" tools/run_ics_tests.mjs
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

const { IcsParser } = await import(pathToFileURL(resolve(outDir, 'IcsParser.ts')).href);

// ---------- 断言工具 ----------
let passed = 0;
let failed = 0;
function check(name, cond, detail) {
  if (cond) { passed++; console.log(`  ok  ${name}`); }
  else { failed++; console.error(`FAIL  ${name}${detail === undefined ? '' : ' :: ' + detail}`); }
}
const eq = (a, b) => JSON.stringify(a) === JSON.stringify(b);
function ics(...lines) { return lines.join('\r\n'); }
function dates(tasks) { return tasks.map(t => t.date); }
function d(y, m, day) { return `${y}-${String(m).padStart(2, '0')}-${String(day).padStart(2, '0')}`; }

// ---------- 19 例 ----------

// 1. weeklySingleByDay_acrossSemester
{
  const tasks = IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'VERSION:2.0', 'BEGIN:VEVENT',
    'SUMMARY:高等数学',
    'DTSTART;TZID=Asia/Shanghai:20260907T083000',
    'DTEND;TZID=Asia/Shanghai:20260907T100000',
    'RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20261228T000000Z',
    'LOCATION:303教室',
    'DESCRIPTION:张老师',
    'END:VEVENT', 'END:VCALENDAR'));
  check('weekly/size17', tasks.length === 17, `got ${tasks.length}`);
  check('weekly/first', eq(tasks[0].date, d(2026, 9, 7)));
  check('weekly/last', eq(tasks[tasks.length - 1].date, d(2026, 12, 28)));
  check('weekly/title', tasks[0].title === '高等数学');
  check('weekly/startTime', tasks[0].startTime === '08:30', tasks[0].startTime);
  check('weekly/endTime', tasks[0].endTime === '10:00', tasks[0].endTime);
  check('weekly/allMonday', tasks.every(t => new Date(t.date + 'T00:00:00Z').getUTCDay() === 1));
}

// 2. multiDayByDay
{
  const tasks = IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT',
    'SUMMARY:英语', 'DTSTART:20260907T100000', 'DTEND:20260907T113000',
    'RRULE:FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20260930',
    'END:VEVENT', 'END:VCALENDAR'));
  check('multiDay', eq(dates(tasks), [
    d(2026, 9, 7), d(2026, 9, 9), d(2026, 9, 14), d(2026, 9, 16),
    d(2026, 9, 21), d(2026, 9, 23), d(2026, 9, 28), d(2026, 9, 30)]));
}

// 3. untilBound inclusive
{
  const parseWith = (until) => IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT', 'SUMMARY:课', 'DTSTART:20260907T083000',
    `RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=${until}`,
    'END:VEVENT', 'END:VCALENDAR'));
  check('until/datetimeZ', eq(dates(parseWith('20260914T000000Z')), [d(2026, 9, 7), d(2026, 9, 14)]));
  check('until/date', eq(dates(parseWith('20260914')), [d(2026, 9, 7), d(2026, 9, 14)]));
  check('until/dayBefore', eq(dates(parseWith('20260913')), [d(2026, 9, 7)]));
}

// 4. countBound
{
  const tasks = IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT', 'SUMMARY:英语',
    'DTSTART:20260907T100000', 'DTEND:20260907T113000',
    'RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=5',
    'END:VEVENT', 'END:VCALENDAR'));
  check('count', eq(dates(tasks), [
    d(2026, 9, 7), d(2026, 9, 9), d(2026, 9, 14), d(2026, 9, 16), d(2026, 9, 21)]));
}

// 5. byweekno ODD
{
  const tasks = IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT', 'SUMMARY:体育',
    'DTSTART:20260907T140000', 'DTEND:20260907T154000',
    'RRULE:FREQ=WEEKLY;BYDAY=MO;BYWEEKNO=ODD;UNTIL=20261102T000000Z',
    'END:VEVENT', 'END:VCALENDAR'));
  check('oddWeek', eq(dates(tasks), [
    d(2026, 9, 7), d(2026, 9, 21), d(2026, 10, 5), d(2026, 10, 19), d(2026, 11, 2)]));
  check('oddWeek/excluded', !dates(tasks).includes(d(2026, 9, 14)) && !dates(tasks).includes(d(2026, 9, 28)));
}

// 6. exdate across lines and commas
{
  const tasks = IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT', 'SUMMARY:课', 'DTSTART:20260907T083000',
    'RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20260928T000000Z',
    'EXDATE:20260914,20260921',
    'EXDATE:20260928T083000Z',
    'END:VEVENT', 'END:VCALENDAR'));
  check('exdate', eq(dates(tasks), [d(2026, 9, 7)]));
}

// 7. two VEVENTs expand independently
{
  const tasks = IcsParser.parse(ics(
    'BEGIN:VCALENDAR',
    'BEGIN:VEVENT', 'SUMMARY:高等数学', 'LOCATION:303教室', 'DESCRIPTION:张老师',
    'DTSTART:20260907T080000', 'DTEND:20260907T094000',
    'RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20261026', 'END:VEVENT',
    'BEGIN:VEVENT', 'SUMMARY:高等数学', 'LOCATION:304教室', 'DESCRIPTION:李老师',
    'DTSTART:20261102T100000', 'DTEND:20261102T114000',
    'RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20261228', 'END:VEVENT',
    'END:VCALENDAR'));
  const firstHalf = tasks.filter(t => t.date <= d(2026, 10, 26));
  const secondHalf = tasks.filter(t => t.date >= d(2026, 11, 2));
  check('twoEvents/size', tasks.length === 17, `got ${tasks.length}`);
  check('twoEvents/split', firstHalf.length === 8 && secondHalf.length === 9);
  check('twoEvents/times', firstHalf.every(t => t.startTime === '08:00' && t.endTime === '09:40')
    && secondHalf.every(t => t.startTime === '10:00' && t.endTime === '11:40'));
  check('twoEvents/loc', firstHalf.every(t => t.location === '303教室' && t.description === '张老师')
    && secondHalf.every(t => t.location === '304教室' && t.description === '李老师'));
}

// 8. all-day event → null times
{
  const tasks = IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT', 'SUMMARY:军训',
    'DTSTART;VALUE=DATE:20260910', 'DTEND;VALUE=DATE:20260910',
    'END:VEVENT', 'END:VCALENDAR'));
  check('allDay', tasks.length === 1 && eq(tasks[0].date, d(2026, 9, 10))
    && tasks[0].startTime === null && tasks[0].endTime === null);
}

// 9. description synthesis + location
{
  const event = (location, note) => {
    const lines = ['BEGIN:VCALENDAR', 'BEGIN:VEVENT', 'SUMMARY:课', 'DTSTART:20260907T080000'];
    if (location !== null) lines.push(`LOCATION:${location}`);
    if (note !== null) lines.push(`DESCRIPTION:${note}`);
    lines.push('END:VEVENT', 'END:VCALENDAR');
    return IcsParser.parse(lines.join('\r\n'))[0];
  };
  check('desc/locTrim', event(' 303教室 ', null).location === '303教室');
  check('desc/locBlankNull', event(' ', '张老师').location === null);
  check('desc/rawKept', event(null, '张老师').description === '张老师');
  check('desc/registry', event('305-106',
    '全称：数值计算方法；教师：王红霞；周次：第3\\,7-9\\,11-12周（第1周为8月31日）').description
    === '教师：王红霞；全称：数值计算方法');
  check('desc/registryLoc', event('305-106',
    '全称：数值计算方法；教师：王红霞；周次：第3周').location === '305-106');
  check('desc/teacherOnly', event(null, '教师：王红霞；周次：第3周').description === '教师：王红霞');
}

// 10. folded lines
{
  const text = [
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT',
    'SUMMARY:高等数',
    ' 学（一）',
    'DESCRIPTION:第1-16周',
    '\t周一第1-2节',
    'DTSTART:20260907T083000',
    'END:VEVENT', 'END:VCALENDAR'].join('\r\n');
  const task = IcsParser.parse(text)[0];
  check('fold/title', task.title === '高等数学（一）', task.title);
  check('fold/desc', task.description === '第1-16周周一第1-2节', task.description);
}

// 11. escaped characters
{
  const task = IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT',
    'SUMMARY:概率论\\,与统计\\;复习\\\\讲义\\nNote',
    'DTSTART:20260907T083000',
    'END:VEVENT', 'END:VCALENDAR'))[0];
  check('escape', task.title === '概率论,与统计;复习\\讲义\nNote', JSON.stringify(task.title));
}

// 12. BYWEEKNO=EVEN == ODD（锚定 DTSTART 所在周）
{
  const tasks = IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT', 'SUMMARY:体育',
    'DTSTART:20260907T140000',
    'RRULE:FREQ=WEEKLY;BYDAY=MO;BYWEEKNO=EVEN;UNTIL=20261102',
    'END:VEVENT', 'END:VCALENDAR'));
  check('evenWeek', eq(dates(tasks), [
    d(2026, 9, 7), d(2026, 9, 21), d(2026, 10, 5), d(2026, 10, 19), d(2026, 11, 2)]));
}

// 13. BYWEEKNO numeric list
{
  const tasks = IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT', 'SUMMARY:课', 'DTSTART:20260907T083000',
    'RRULE:FREQ=WEEKLY;BYDAY=MO,WE;BYWEEKNO=1,2',
    'END:VEVENT', 'END:VCALENDAR'));
  check('weekNoList', eq(dates(tasks), [
    d(2026, 9, 7), d(2026, 9, 9), d(2026, 9, 14), d(2026, 9, 16)]));
}

// 14. no RRULE → single occurrence
{
  const task = IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT', 'SUMMARY:班会',
    'DTSTART:20260908T152000', 'DTEND:20260908T164000',
    'END:VEVENT', 'END:VCALENDAR'))[0];
  check('single', eq(task.date, d(2026, 9, 8)) && task.startTime === '15:20' && task.endTime === '16:40');
}

// 15. no UNTIL/COUNT → 365 天上界
{
  const tasks = IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT', 'SUMMARY:长课', 'DTSTART:20260907T083000',
    'RRULE:FREQ=WEEKLY;BYDAY=MO',
    'END:VEVENT', 'END:VCALENDAR'));
  check('span365', tasks.length === 53, `got ${tasks.length}`);
  check('span365/last', eq(tasks[tasks.length - 1].date, d(2027, 9, 6)));
}

// 16. BYDAY 缺省用 DTSTART 星期
{
  const tasks = IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT', 'SUMMARY:课',
    'DTSTART:20260909T101000',
    'RRULE:FREQ=WEEKLY;UNTIL=20261001T000000Z',
    'END:VEVENT', 'END:VCALENDAR'));
  check('bydayDefault', eq(dates(tasks), [
    d(2026, 9, 9), d(2026, 9, 16), d(2026, 9, 23), d(2026, 9, 30)]));
}

// 17. RDATE 无 RRULE：显式日期全展开
{
  const tasks = IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT', 'SUMMARY:数值',
    'DTSTART;TZID=Asia/Shanghai:20260915T080000',
    'DTEND;TZID=Asia/Shanghai:20260915T094000',
    'RDATE;TZID=Asia/Shanghai:20261013T080000,20261020T080000,20261027T080000,',
    ' 20261110T080000,20261117T080000',
    'RDATE;TZID=Asia/Shanghai:20261201T080000',
    'LOCATION:305-106',
    'DESCRIPTION:全称：数值计算方法；教师：王红霞；周次：第3周',
    'END:VEVENT', 'END:VCALENDAR'));
  check('rdate/dates', eq(dates(tasks), [
    d(2026, 9, 15), d(2026, 10, 13), d(2026, 10, 20), d(2026, 10, 27),
    d(2026, 11, 10), d(2026, 11, 17), d(2026, 12, 1)]));
  check('rdate/times', tasks.every(t => t.startTime === '08:00' && t.endTime === '09:40'));
  check('rdate/loc', tasks.every(t => t.location === '305-106'));
  check('rdate/desc', tasks.every(t => t.description === '教师：王红霞；全称：数值计算方法'));
}

// 18. RDATE 与 RRULE 并集去重
{
  const tasks = IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT', 'SUMMARY:课',
    'DTSTART:20260907T080000',
    'RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20260921',
    'RDATE:20260909T080000',
    'RDATE:20260914T080000',
    'END:VEVENT', 'END:VCALENDAR'));
  check('rdate+rrule', eq(dates(tasks), [
    d(2026, 9, 7), d(2026, 9, 9), d(2026, 9, 14), d(2026, 9, 21)]));
}

// 19. RDATE 不受 COUNT 限制、可被 EXDATE 排除
{
  const tasks = IcsParser.parse(ics(
    'BEGIN:VCALENDAR', 'BEGIN:VEVENT', 'SUMMARY:课',
    'DTSTART:20260907T080000',
    'RRULE:FREQ=WEEKLY;BYDAY=MO;COUNT=2',
    'RDATE:20261005T080000,20261102T080000',
    'EXDATE:20261102',
    'END:VEVENT', 'END:VCALENDAR'));
  check('rdate/count/exdate', eq(dates(tasks), [
    d(2026, 9, 7), d(2026, 9, 14), d(2026, 10, 5)]));
}

process.exitCode = failed === 0 ? 0 : 1;
