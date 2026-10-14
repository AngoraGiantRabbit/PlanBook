import { DateUtils } from '../common/DateUtils';

/**
 * ICS（RFC 5545 子集）解析 + RRULE 展开，面向课程表导入（ADR-0005）。
 * Android 版 data/ics/IcsParser.kt 的忠实移植；纯逻辑，不依赖 @ohos API。
 *
 * 语义要点（与 Kotlin 版逐条对齐）：
 * - 折行展开、VEVENT 内嵌套子块忽略、属性参数丢弃。
 * - 日期时间三形态：YYYYMMDDTHHMMSS[Z] / YYYYMMDD（全天）。
 * - RRULE 仅 FREQ=WEEKLY；BYDAY/UNTIL/COUNT/BYWEEKNO（数字列表与 ODD/EVEN 单双周）。
 * - RDATE 显式实例并集去重、不受 COUNT/UNTIL/365 上界约束、可被 EXDATE 排除。
 * - DESCRIPTION「教师：/全称：」重组；文本反转义。
 */

/** ICS 导入展开后的单天任务（ADR-0005） */
export class IcsTask {
  title: string = '';
  description: string = '';
  location: string | null = null;
  date: string = '';       // yyyy-MM-dd
  startTime: string | null = null; // HH:mm
  endTime: string | null = null;   // HH:mm

  constructor(title: string, description: string, location: string | null,
    date: string, startTime: string | null, endTime: string | null) {
    this.title = title;
    this.description = description;
    this.location = location;
    this.date = date;
    this.startTime = startTime;
    this.endTime = endTime;
  }
}

class DateTimeVal {
  date: string = '';
  time: string | null = null; // "HH:mm"

  constructor(date: string, time: string | null) {
    this.date = date;
    this.time = time;
  }
}

class WeeklyRule {
  byDay: number[] | null = null;  // ISO 1..7；null = 用 DTSTART 自己的星期几
  until: string | null = null;    // yyyy-MM-dd，闭区间上界（只比日期）
  count: number | null = null;    // 生成阶段总出现数上界
  weekNumbers: Set<number> | null = null; // 学期相对周次白名单
  biweekly: boolean = false;      // ODD/EVEN：每 2 周，锚定 DTSTART 所在周
}

export class IcsParser {
  /** 无 UNTIL/COUNT 时的展开上界：DTSTART + 365 天（含） */
  private static readonly MAX_SPAN_DAYS = 365;
  /** 生成循环的周数硬上界（100 年），防御异常输入 */
  private static readonly MAX_WEEKS = 5200;

  private static readonly DAY_CODES: Record<string, number> = {
    'MO': 1, 'TU': 2, 'WE': 3, 'TH': 4, 'FR': 5, 'SA': 6, 'SU': 7
  };

  /** 解析 ICS 文本，返回全部 VEVENT 展开后的单天任务（日期升序、同日 startTime 升序） */
  static parse(text: string): IcsTask[] {
    const tasks: IcsTask[] = [];
    const eventProps: string[][] = []; // [属性名(大写), 原始值]
    let inEvent = false;
    let nestedDepth = 0;

    const lines = IcsParser.unfold(text);
    for (const line of lines) {
      const upper = line.toUpperCase();
      if (!inEvent && upper === 'BEGIN:VEVENT') {
        inEvent = true;
        nestedDepth = 0;
        eventProps.length = 0;
      } else if (inEvent && nestedDepth > 0 && upper.startsWith('END:')) {
        nestedDepth--;
      } else if (inEvent && nestedDepth > 0) {
        // 嵌套子块（VALARM 等）内容忽略
      } else if (inEvent && upper === 'END:VEVENT') {
        const expanded = IcsParser.expandEvent(eventProps);
        for (const t of expanded) {
          tasks.push(t);
        }
        inEvent = false;
      } else if (inEvent && upper.startsWith('BEGIN:')) {
        nestedDepth++;
      } else if (inEvent) {
        const kv = IcsParser.splitContentLine(line);
        if (kv !== null) {
          eventProps.push(kv);
        }
      }
    }
    tasks.sort((a, b) => {
      const dc = DateUtils.cmp(a.date, b.date);
      if (dc !== 0) {
        return dc;
      }
      const at = a.startTime ?? '';
      const bt = b.startTime ?? '';
      return at === bt ? 0 : (at < bt ? -1 : 1);
    });
    return tasks;
  }

  /** RFC 5545 折行展开：以空格/制表符开头的行接续上一行（去掉一个前导空白字符） */
  private static unfold(text: string): string[] {
    const out: string[] = [];
    const pieces = text.split(/\r\n|\n|\r/);
    for (const piece of pieces) {
      if ((piece.startsWith(' ') || piece.startsWith('\t')) && out.length > 0) {
        out[out.length - 1] = out[out.length - 1] + piece.substring(1);
      } else if (piece.length > 0) {
        out.push(piece);
      }
    }
    return out;
  }

  /** 拆为 [属性名(大写), 值]；属性参数（第一个 ':' 前分号后的部分）丢弃 */
  private static splitContentLine(line: string): string[] | null {
    const colon = line.indexOf(':');
    if (colon <= 0) {
      return null;
    }
    const head = line.substring(0, colon);
    const semi = head.indexOf(';');
    const name = (semi >= 0 ? head.substring(0, semi) : head).trim().toUpperCase();
    if (name.length === 0) {
      return null;
    }
    return [name, line.substring(colon + 1)];
  }

  private static first(props: string[][], name: string): string | null {
    for (const kv of props) {
      if (kv[0] === name) {
        return kv[1];
      }
    }
    return null;
  }

  /** 把单个 VEVENT 的原始属性列表展开为单天任务列表 */
  private static expandEvent(props: string[][]): IcsTask[] {
    const dtStartRaw = IcsParser.first(props, 'DTSTART');
    const dtStart = dtStartRaw === null ? null : IcsParser.parseDateTimeValue(dtStartRaw);
    if (dtStart === null) {
      return []; // 无 DTSTART / 无法解析：跳过该事件
    }
    const dtEndRaw = IcsParser.first(props, 'DTEND');
    const dtEnd = dtEndRaw === null ? null : IcsParser.parseDateTimeValue(dtEndRaw);
    const titleRaw = IcsParser.first(props, 'SUMMARY');
    const title = titleRaw === null ? '' : IcsParser.unescape(titleRaw).trim();
    const locRaw = IcsParser.first(props, 'LOCATION');
    const locTrimmed = locRaw === null ? '' : IcsParser.unescape(locRaw).trim();
    const location = locTrimmed.length > 0 ? locTrimmed : null;
    const descRaw = IcsParser.first(props, 'DESCRIPTION');
    const description = descRaw === null ? '' : IcsParser.buildDescription(IcsParser.unescape(descRaw).trim());
    const ruleRaw = IcsParser.first(props, 'RRULE');
    const rule = ruleRaw === null ? null : IcsParser.parseRule(ruleRaw);
    const exdates = new Set<string>();
    const rdates = new Set<string>();
    for (const kv of props) {
      if (kv[0] === 'EXDATE') {
        for (const v of kv[1].split(',')) {
          const parsed = IcsParser.parseDateTimeValue(v);
          if (parsed !== null) {
            exdates.add(parsed.date);
          }
        }
      } else if (kv[0] === 'RDATE') {
        for (const v of kv[1].split(',')) {
          const parsed = IcsParser.parseDateTimeValue(v);
          if (parsed !== null) {
            rdates.add(parsed.date);
          }
        }
      }
    }
    const dates = IcsParser.expandDates(dtStart.date, rule, exdates, rdates);
    const out: IcsTask[] = [];
    for (const d of dates) {
      out.push(new IcsTask(title, description, location, d, dtStart.time, dtEnd === null ? null : dtEnd.time));
    }
    return out;
  }

  /** 「教师：X」「全称：Y」段抽取（分号/换行分隔），命中任一则重组、周次等内容丢弃 */
  private static buildDescription(raw: string): string {
    if (raw.length === 0) {
      return raw;
    }
    const teacher = IcsParser.matchSegment(raw, '教师：');
    const fullName = IcsParser.matchSegment(raw, '全称：');
    if (teacher === null && fullName === null) {
      return raw;
    }
    const parts: string[] = [];
    if (teacher !== null && teacher.length > 0) {
      parts.push(`教师：${teacher}`);
    }
    if (fullName !== null && fullName.length > 0) {
      parts.push(`全称：${fullName}`);
    }
    return parts.join('；');
  }

  /** 找「prefix 后到下一个 ;/；/换行」的片段；没有 prefix 返回 null */
  private static matchSegment(raw: string, prefix: string): string | null {
    const i = raw.indexOf(prefix);
    if (i < 0) {
      return null;
    }
    const j = i + prefix.length;
    let end = j;
    while (end < raw.length) {
      const c = raw.charAt(end);
      if (c === ';' || c === '；' || c === '\n') {
        break;
      }
      end++;
    }
    return raw.substring(j, end).trim();
  }

  /** 解析 `20260907T083000Z` / `20260907T083000` / `20260907`（全天，time=null） */
  private static parseDateTimeValue(raw: string): DateTimeVal | null {
    const trimmed = raw.trim();
    const body = trimmed.toUpperCase().endsWith('Z') ? trimmed.substring(0, trimmed.length - 1) : trimmed;
    if (body.length === 8) {
      const date = DateUtils.fromBasic(body);
      return date === null ? null : new DateTimeVal(date, null);
    }
    if (body.length === 15 && body.charAt(8) === 'T') {
      const date = DateUtils.fromBasic(body.substring(0, 8));
      const hhmmss = body.substring(9);
      if (date === null || hhmmss.length !== 6) {
        return null;
      }
      const h = Number(hhmmss.substring(0, 2));
      const m = Number(hhmmss.substring(2, 4));
      const s = Number(hhmmss.substring(4, 6));
      if (!Number.isFinite(h) || !Number.isFinite(m) || !Number.isFinite(s) || h > 23 || m > 59 || s > 59) {
        return null;
      }
      return new DateTimeVal(date, DateUtils.minutesToTime(h * 60 + m));
    }
    return null;
  }

  /** UNTIL 值只取日期部分（时间分量忽略）；无法解析返回 null（视为未设置） */
  private static parseUntil(value: string): string | null {
    const trimmed = value.trim();
    const body = trimmed.toUpperCase().endsWith('Z') ? trimmed.substring(0, trimmed.length - 1) : trimmed;
    if (body.length < 8) {
      return null;
    }
    return DateUtils.fromBasic(body.substring(0, 8));
  }

  /** 仅支持 FREQ=WEEKLY；其他 FREQ 返回 null（退化为 DTSTART 单次出现） */
  private static parseRule(raw: string): WeeklyRule | null {
    let freq: string | null = null;
    let byDay: number[] | null = null;
    let until: string | null = null;
    let count: number | null = null;
    let weekNumbers: Set<number> | null = null;
    let biweekly = false;
    for (const part of raw.split(';')) {
      const eq = part.indexOf('=');
      if (eq < 0) {
        continue;
      }
      const key = part.substring(0, eq).trim().toUpperCase();
      const value = part.substring(eq + 1).trim();
      if (key === 'FREQ') {
        freq = value.toUpperCase();
      } else if (key === 'BYDAY') {
        const days: number[] = [];
        for (const tok of value.split(',')) {
          const t = tok.trim().toUpperCase();
          const code = t.length >= 2 ? IcsParser.DAY_CODES[t.substring(t.length - 2)] : undefined;
          if (code !== undefined) {
            days.push(code);
          }
        }
        byDay = days.length > 0 ? days : null;
      } else if (key === 'UNTIL') {
        until = IcsParser.parseUntil(value);
      } else if (key === 'COUNT') {
        const n = Number(value);
        count = Number.isFinite(n) && n > 0 && Number.isInteger(n) ? n : null;
      } else if (key === 'BYWEEKNO') {
        const tokens = value.split(',');
        let oddEven = false;
        for (const tok of tokens) {
          const t = tok.trim().toUpperCase();
          if (t === 'ODD' || t === 'EVEN') {
            oddEven = true;
          }
        }
        if (oddEven) {
          biweekly = true;
        } else {
          const nums = new Set<number>();
          for (const tok of tokens) {
            const n = Number(tok.trim());
            if (Number.isFinite(n) && n > 0 && Number.isInteger(n)) {
              nums.add(n);
            }
          }
          weekNumbers = nums.size > 0 ? nums : null;
        }
      }
    }
    if (freq !== null && freq !== 'WEEKLY') {
      return null;
    }
    const rule = new WeeklyRule();
    rule.byDay = byDay;
    rule.until = until;
    rule.count = count;
    rule.weekNumbers = weekNumbers;
    rule.biweekly = biweekly;
    return rule;
  }

  /**
   * 从 DTSTART 起按周生成出现日期（RRULE），与 RDATE 显式实例并集去重，
   * 再移除 EXDATE。逐周（ISO 周一起算）、周内按星期升序迭代，保证时间有序，
   * 命中 UNTIL/COUNT/365 天上界即停；RDATE 不受这些上界约束。
   */
  private static expandDates(dtStart: string, rule: WeeklyRule | null,
    exdates: Set<string>, rdates: Set<string>): string[] {
    let rruleDates: string[];
    if (rule === null) {
      rruleDates = [dtStart];
    } else {
      const weekdays = (rule.byDay ?? [DateUtils.dayOfWeek(dtStart)]).slice().sort((a, b) => a - b);
      const firstWeekMonday = DateUtils.weekStartOf(dtStart);
      const hardEnd = DateUtils.addDays(dtStart, IcsParser.MAX_SPAN_DAYS);
      let maxWeekNo: number | null = null;
      if (rule.weekNumbers !== null) {
        for (const n of rule.weekNumbers) {
          if (maxWeekNo === null || n > maxWeekNo) {
            maxWeekNo = n;
          }
        }
      }
      const out: string[] = [];
      let week = 0;
      outer: while (week <= IcsParser.MAX_WEEKS) {
        const weekNo = week + 1;
        if (rule.biweekly && week % 2 === 1) {
          week++;
          continue;
        }
        if (rule.weekNumbers !== null && !rule.weekNumbers.has(weekNo)) {
          if (maxWeekNo !== null && weekNo > maxWeekNo) {
            break outer; // 已超过列表最大周次
          }
          week++;
          continue;
        }
        const weekMonday = DateUtils.addWeeks(firstWeekMonday, week);
        for (const day of weekdays) {
          const date = DateUtils.addDays(weekMonday, day - 1);
          if (DateUtils.cmp(date, dtStart) < 0) {
            continue; // 首周里早于 DTSTART 的日期不出现
          }
          if (rule.count !== null && out.length >= rule.count) {
            break outer;
          }
          if (rule.until !== null && DateUtils.cmp(date, rule.until) > 0) {
            break outer;
          }
          if (rule.until === null && rule.count === null && DateUtils.cmp(date, hardEnd) > 0) {
            break outer;
          }
          out.push(date);
        }
        week++;
      }
      rruleDates = out;
    }
    const merged = new Set<string>(rruleDates);
    for (const d of rdates) {
      merged.add(d);
    }
    const result: string[] = [];
    for (const d of merged) {
      if (!exdates.has(d)) {
        result.push(d);
      }
    }
    result.sort((a, b) => DateUtils.cmp(a, b));
    return result;
  }

  /** RFC 5545 文本反转义：`\n`/`\N`→换行、`\,`、`\;`、`\\`；其他 `\x` 原样保留 */
  private static unescape(text: string): string {
    let out = '';
    let i = 0;
    while (i < text.length) {
      const c = text.charAt(i);
      if (c === '\\' && i + 1 < text.length) {
        const n = text.charAt(i + 1);
        if (n === 'n' || n === 'N') {
          out += '\n';
        } else if (n === ',' || n === ';' || n === '\\') {
          out += n;
        } else {
          out += c;
          out += n;
        }
        i += 2;
      } else {
        out += c;
        i++;
      }
    }
    return out;
  }
}
