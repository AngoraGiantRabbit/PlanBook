/**
 * 纯日期工具：yyyy-MM-dd 字符串语义（与 Android 版 java.time 行为对齐）。
 * 不依赖任何 @ohos API，可在 Node 下直接运行（供单测）。
 * 星期采用 ISO 语义：周一=1 ... 周日=7（与 java.time.DayOfWeek.value 一致）。
 */
export class DateUtils {
  /** 今天 → "yyyy-MM-dd" */
  static today(): string {
    const d = new Date();
    return DateUtils.fromParts(d.getFullYear(), d.getMonth() + 1, d.getDate());
  }

  static fromParts(y: number, m: number, d: number): string {
    const s1 = `${y}`;
    const year = s1.padStart(4, '0');
    const month = `${m}`.padStart(2, '0');
    const day = `${d}`.padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  /** "yyyy-MM-dd" → Ymd；非法输入返回 null */
  static toParts(s: string): Ymd | null {
    if (s.length < 10) {
      return null;
    }
    const y = Number(s.substring(0, 4));
    const m = Number(s.substring(5, 7));
    const d = Number(s.substring(8, 10));
    if (!Number.isFinite(y) || !Number.isFinite(m) || !Number.isFinite(d) || m < 1 || m > 12 || d < 1 || d > 31) {
      return null;
    }
    if (d > DateUtils.monthLength(y, m)) {
      return null;
    }
    return new Ymd(y, m, d);
  }

  static isValid(s: string): boolean {
    return DateUtils.toParts(s) !== null;
  }

  /** 某年某月天数（公历） */
  static monthLength(y: number, m: number): number {
    if (m === 2) {
      const leap = (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0;
      return leap ? 29 : 28;
    }
    if (m === 4 || m === 6 || m === 9 || m === 11) {
      return 30;
    }
    return 31;
  }

  /** epoch 天数（仅日期分量，UTC），用于加减与比较 */
  static toEpochDay(s: string): number {
    const p = DateUtils.toParts(s);
    if (p === null) {
      return Number.NaN;
    }
    return Math.floor(Date.UTC(p.y, p.m - 1, p.d) / 86400000);
  }

  static fromEpochDay(epochDay: number): string {
    const d = new Date(epochDay * 86400000);
    return DateUtils.fromParts(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate());
  }

  static addDays(s: string, days: number): string {
    return DateUtils.fromEpochDay(DateUtils.toEpochDay(s) + days);
  }

  /** 加 n 个月（月内日截断到目标月最后一天，与 java.time.plusMonths 一致） */
  static addMonths(s: string, months: number): string {
    const p = DateUtils.toParts(s);
    if (p === null) {
      return s;
    }
    const total = p.y * 12 + (p.m - 1) + months;
    const y = Math.floor(total / 12);
    const m = total - y * 12 + 1;
    const d = Math.min(p.d, DateUtils.monthLength(y, m));
    return DateUtils.fromParts(y, m, d);
  }

  /** ISO 星期：周一=1 ... 周日=7 */
  static dayOfWeek(s: string): number {
    const epochDay = DateUtils.toEpochDay(s);
    // 1970-01-01 是周四（ISO 4）：+3 对齐到周一=1，模 7
    return (((epochDay + 3) % 7) + 7) % 7 + 1;
  }

  /** 该日所在周的周一 */
  static weekStartOf(s: string): string {
    return DateUtils.addDays(s, -(DateUtils.dayOfWeek(s) - 1));
  }

  /** 当月第一天 */
  static monthStartOf(s: string): string {
    return s.substring(0, 8) + '01';
  }

  /** 当月最后一天 */
  static monthEndOf(s: string): string {
    const p = DateUtils.toParts(s);
    if (p === null) {
      return s;
    }
    return DateUtils.fromParts(p.y, p.m, DateUtils.monthLength(p.y, p.m));
  }

  /** 字典序即时间序（yyyy-MM-dd 定宽），NaN 无效日一律排后 */
  static cmp(a: string, b: string): number {
    const da = DateUtils.toEpochDay(a);
    const db = DateUtils.toEpochDay(b);
    if (Number.isNaN(da) && Number.isNaN(db)) {
      return 0;
    }
    if (Number.isNaN(da)) {
      return 1;
    }
    if (Number.isNaN(db)) {
      return -1;
    }
    return da === db ? 0 : (da < db ? -1 : 1);
  }

  /** 查看日 day 是否落在 [start..end]（任一无效日期返回 false） */
  static within(day: string, start: string, end: string): boolean {
    return DateUtils.cmp(day, start) >= 0 && DateUtils.cmp(day, end) <= 0;
  }

  /** "MM/dd"（表头/周范围显示用） */
  static md(s: string): string {
    return s.length >= 10 ? s.substring(5, 7) + '/' + s.substring(8, 10) : s;
  }

  /** "M月d日" */
  static cnDate(s: string): string {
    const p = DateUtils.toParts(s);
    if (p === null) {
      return s;
    }
    return `${p.m}月${p.d}日`;
  }

  /** 周几中文（一/二/…/日），ISO 1..7 */
  static weekdayCn(iso: number): string {
    const names = ['一', '二', '三', '四', '五', '六', '日'];
    const idx = iso - 1;
    return idx >= 0 && idx < 7 ? names[idx] : '?';
  }

  /** "yyyy年M月" */
  static cnMonth(s: string): string {
    const p = DateUtils.toParts(s);
    if (p === null) {
      return s;
    }
    return `${p.y}年 ${p.m}月`;
  }

  /** "yyyy年M月d日 周X" */
  static cnFull(s: string): string {
    const p = DateUtils.toParts(s);
    if (p === null) {
      return s;
    }
    return `${p.y}年${p.m}月${p.d}日 周${DateUtils.weekdayCn(DateUtils.dayOfWeek(s))}`;
  }

  /** "YYYYMMDD"（BASIC_ISO_DATE）→ "yyyy-MM-dd"；非法返回 null */
  static fromBasic(basic: string): string | null {
    if (basic.length !== 8) {
      return null;
    }
    const y = Number(basic.substring(0, 4));
    const m = Number(basic.substring(4, 6));
    const d = Number(basic.substring(6, 8));
    if (!Number.isFinite(y) || !Number.isFinite(m) || !Number.isFinite(d)) {
      return null;
    }
    if (m < 1 || m > 12 || d < 1 || d > DateUtils.monthLength(y, m)) {
      return null;
    }
    return DateUtils.fromParts(y, m, d);
  }

  static addWeeks(s: string, weeks: number): string {
    return DateUtils.addDays(s, weeks * 7);
  }

  /** 某周（周一起）7 天日期串 */
  static weekDays(weekStart: string): string[] {
    const out: string[] = [];
    for (let i = 0; i < 7; i++) {
      out.push(DateUtils.addDays(weekStart, i));
    }
    return out;
  }

  /** "HH:mm" → 当日分钟数；非法返回 0 */
  static timeToMinutes(hhmm: string): number {
    const parts = hhmm.split(':');
    if (parts.length !== 2) {
      return 0;
    }
    const h = Number(parts[0]);
    const m = Number(parts[1]);
    if (!Number.isFinite(h) || !Number.isFinite(m)) {
      return 0;
    }
    return h * 60 + m;
  }

  /** 分钟数 → "HH:mm" */
  static minutesToTime(min: number): string {
    const h = Math.floor(min / 60);
    const m = min - h * 60;
    return `${h}`.padStart(2, '0') + ':' + `${m}`.padStart(2, '0');
  }

  /** epoch 毫秒 → 当地 "yyyy-MM-dd"（completedAt → 完成日） */
  static fromEpochMillis(ms: number): string {
    const d = new Date(ms);
    return DateUtils.fromParts(d.getFullYear(), d.getMonth() + 1, d.getDate());
  }
}

/** 日期分量（toParts 的显式类型；ArkTS 禁止匿名对象字面量类型） */
export class Ymd {
  y: number;
  m: number;
  d: number;

  constructor(y: number, m: number, d: number) {
    this.y = y;
    this.m = m;
    this.d = d;
  }
}
