/**
 * 主题令牌：对应 Android 版 Material3 默认调色（浅色）。
 * 自定义控件统一从这里取色；系统控件颜色用属性覆盖对齐。
 */

export class AppColors {
  static readonly primary: string = '#6750A4';
  static readonly onPrimary: string = '#FFFFFF';
  static readonly primaryContainer: string = '#EADDFF';
  static readonly onPrimaryContainer: string = '#21005D';
  static readonly tertiary: string = '#7D5260';
  static readonly tertiaryContainer: string = '#FFD8E4';
  static readonly onTertiaryContainer: string = '#31111D';
  static readonly surface: string = '#FEF7FF';
  static readonly onSurface: string = '#1D1B20';
  static readonly surfaceVariant: string = '#E7E0EC';
  static readonly onSurfaceVariant: string = '#49454F';
  static readonly outline: string = '#79747E';
  static readonly error: string = '#B3261E';
  static readonly scrim: string = '#000000';
  static readonly gridLine: string = '#33000000';        // 网格线（黑 20%）
  static readonly gridColumnBg: string = '#26D3D3D3';    // 日列底色（浅灰 15%）
  static readonly completedText: string = '#9E9E9E';
  static readonly widgetTodoBg: string = '#F5F1FA';
  static readonly widgetTodoAccent: string = '#4A3B8C';
  static readonly widgetTodayBg: string = '#E8DEF8';
}

/** "#RRGGBB" 色值工具 */
export class ColorUtil {
  private static hexRegex = /^#[0-9A-Fa-f]{6}$/;

  static isHex(hex: string | null): boolean {
    return hex !== null && ColorUtil.hexRegex.test(hex);
  }

  /** "#RRGGBB" → "#AARRGGBB"（alpha 0..1） */
  static withAlpha(hex: string | null, alpha: number): string {
    if (hex === null || !ColorUtil.hexRegex.test(hex)) {
      return '#809E9E9E';
    }
    const a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
    return `#${a.toString(16).toUpperCase().padStart(2, '0')}${hex.substring(1).toUpperCase()}`;
  }

  /** 无效色回退灰 */
  static safe(hex: string | null): string {
    return hex !== null && ColorUtil.hexRegex.test(hex) ? hex : '#9E9E9E';
  }

  /** HSV → "#RRGGBB"（自定义调色盘用） */
  static hsv(h: number, s: number, v: number): string {
    const hh = ((h % 360) + 360) % 360 / 60;
    const c = v * s;
    const x = c * (1 - Math.abs(hh % 2 - 1));
    let r = 0;
    let g = 0;
    let b = 0;
    if (hh >= 0 && hh < 1) {
      r = c; g = x; b = 0;
    } else if (hh < 2) {
      r = x; g = c; b = 0;
    } else if (hh < 3) {
      r = 0; g = c; b = x;
    } else if (hh < 4) {
      r = 0; g = x; b = c;
    } else if (hh < 5) {
      r = x; g = 0; b = c;
    } else {
      r = c; g = 0; b = x;
    }
    const m = v - c;
    const to255 = (t: number): string => Math.round((t + m) * 255).toString(16).toUpperCase().padStart(2, '0');
    return `#${to255(r)}${to255(g)}${to255(b)}`;
  }

  /** "#RRGGBB" → [h, s, v]（0..360 / 0..1 / 0..1） */
  static toHsv(hex: string | null): number[] {
    const h = ColorUtil.safe(hex);
    const r = parseInt(h.substring(1, 3), 16) / 255;
    const g = parseInt(h.substring(3, 5), 16) / 255;
    const b = parseInt(h.substring(5, 7), 16) / 255;
    const max = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    const d = max - min;
    let hue = 0;
    if (d !== 0) {
      if (max === r) {
        hue = ((g - b) / d) % 6;
      } else if (max === g) {
        hue = (b - r) / d + 2;
      } else {
        hue = (r - g) / d + 4;
      }
      hue *= 60;
      if (hue < 0) {
        hue += 360;
      }
    }
    return [hue, max === 0 ? 0 : d / max, max];
  }
}

export class Dimens {
  /** 周视图一小时格高（vp） */
  static readonly hourHeight: number = 56;
  /** 周视图时段起点（06:00） */
  static readonly startHour: number = 6;
  /** 周视图时段终点（24:00，不含） */
  static readonly endHour: number = 24;
}
