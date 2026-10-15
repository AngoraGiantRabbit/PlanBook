import { Task } from '../model/Models';
import { common } from '@kit.AbilityKit';
import { picker } from '@kit.CoreFileKit';
import fs from '@ohos.file.fs';
import util from '@ohos.util';

/**
 * ICS 导出（RFC 5545 子集，与 IcsParser 对应）：
 * - 每个任务一个 VEVENT；有时段输出 DATE-TIME（本地浮动时间），无时段输出全天 VALUE=DATE；
 * - 文本按 RFC 转义（逗号/分号/反斜杠/换行）；CRLF 换行；
 * - buildContent 为纯函数（可单测：导出→IcsParser.parse 应还原）。
 * 保存：DocumentViewPicker 由用户选位置，写 UTF-8 文本。
 */
export class IcsExporter {
  /** 任务列表 → ICS 文本（纯函数） */
  static buildContent(tasks: Task[], calName: string): string {
    const lines: string[] = [];
    lines.push('BEGIN:VCALENDAR');
    lines.push('VERSION:2.0');
    lines.push('PRODID:-//PlanBook//HarmonyOS//CN');
    lines.push(`X-WR-CALNAME:${IcsExporter.escape(calName)}`);
    for (const t of tasks) {
      lines.push('BEGIN:VEVENT');
      lines.push(`SUMMARY:${IcsExporter.escape(t.title)}`);
      if (t.startTime !== null && t.startTime.length >= 4) {
        lines.push(`DTSTART:${IcsExporter.dateBasic(t.startDate)}T${IcsExporter.timeBasic(t.startTime)}00`);
        const end = t.endTime !== null && t.endTime.length >= 4 ? t.endTime : t.startTime;
        lines.push(`DTEND:${IcsExporter.dateBasic(t.endDate)}T${IcsExporter.timeBasic(end)}00`);
      } else {
        lines.push(`DTSTART;VALUE=DATE:${IcsExporter.dateBasic(t.startDate)}`);
        lines.push(`DTEND;VALUE=DATE:${IcsExporter.dateBasic(t.endDate)}`);
      }
      if (t.location !== null && t.location.length > 0) {
        lines.push(`LOCATION:${IcsExporter.escape(t.location)}`);
      }
      if (t.description.length > 0) {
        lines.push(`DESCRIPTION:${IcsExporter.escape(t.description)}`);
      }
      lines.push('END:VEVENT');
    }
    lines.push('END:VCALENDAR');
    return lines.join('\r\n') + '\r\n';
  }

  /** yyyy-MM-dd → YYYYMMDD（非法回退当日） */
  private static dateBasic(s: string): string {
    const t = s.length >= 10 ? s.substring(0, 10) : '';
    if (t.charAt(4) !== '-' || t.charAt(7) !== '-') {
      return '20260101';
    }
    return t.substring(0, 4) + t.substring(5, 7) + t.substring(8, 10);
  }

  /** HH:mm → HHMMSS 中的 HHMM（秒补 00 由调用方拼） */
  private static timeBasic(hhmm: string): string {
    const t = hhmm.length >= 5 ? hhmm.substring(0, 5) : '08:00';
    return t.substring(0, 2) + t.substring(3, 5);
  }

  /** RFC 5545 文本转义 */
  private static escape(text: string): string {
    let out = '';
    for (let i = 0; i < text.length; i++) {
      const c = text.charAt(i);
      if (c === '\\' || c === ';' || c === ',') {
        out += '\\';
      }
      if (c === '\n') {
        out += '\\n';
      } else {
        out += c;
      }
    }
    return out;
  }

  /** 保存到用户选择的位置（DocumentSavePicker；取消返回 false） */
  static async saveToFile(context: common.Context, fileName: string, content: string): Promise<boolean> {
    const docPicker = new picker.DocumentViewPicker();
    const uris = await docPicker.save({
      newFileNames: [fileName],
      fileSuffixChoices: ['']
    });
    if (uris === undefined || uris === null || uris.length === 0) {
      return false;
    }
    const file = fs.openSync(uris[0], fs.OpenMode.READ_WRITE | fs.OpenMode.TRUNC);
    try {
      const encoder = util.TextEncoder.create('utf-8');
      const bytes = encoder.encodeInto(content);
      fs.writeSync(file.fd, bytes.buffer);
      return true;
    } finally {
      fs.closeSync(file);
    }
  }
}
