/**
 * 课程备注字段工具：课程任务的 description 按「教师：X；全称：Y」约定存取
 * （ICS 导入由 IcsParser 重组、手动课表由仓库写入，App 侧统一经此解析）。
 * 纯逻辑，不依赖 @ohos API。
 */
export class CourseInfo {
  /** 「教师：」段内容；无该段返回 '' */
  static teacherOf(description: string): string {
    const seg = CourseInfo.matchSegment(description, '教师：');
    return seg === null ? '' : seg;
  }

  /** 「全称：」段内容；无该段返回 '' */
  static fullNameOf(description: string): string {
    const seg = CourseInfo.matchSegment(description, '全称：');
    return seg === null ? '' : seg;
  }

  /**
   * 用新教师值重建课程备注：保留原备注中的「全称：」段，丢弃其余自由文本。
   * 教师与全称皆空 → ''。
   */
  static withTeacher(oldDescription: string, teacher: string): string {
    const parts: string[] = [];
    if (teacher.length > 0) {
      parts.push(`教师：${teacher}`);
    }
    const fullName = CourseInfo.fullNameOf(oldDescription);
    if (fullName.length > 0) {
      parts.push(`全称：${fullName}`);
    }
    return parts.join('；');
  }

  /** 找「prefix 后到下一个 ;/；/换行」的片段；没有 prefix 返回 null（与 IcsParser 同语义） */
  static matchSegment(raw: string, prefix: string): string | null {
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
}
