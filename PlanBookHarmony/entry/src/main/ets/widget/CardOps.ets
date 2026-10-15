/** 卡片内交互助手：翻周/刷新 message、点卡开 App。postCardAction 为双参形式（本 SDK 声明）。 */
export class CardOps {
  /** 发送 message 给 FormAbility（op: prevWeek / nextWeek / refresh） */
  static send(component: Object, op: string): void {
    const params: Record<string, Object | string> = { 'op': op };
    const action: Record<string, Object | string> = { 'action': 'message', 'params': params };
    postCardAction(component, action);
  }

  /** 点卡打开 App */
  static openApp(component: Object): void {
    const action: Record<string, Object | string> = { 'action': 'router', 'abilityName': 'EntryAbility' };
    postCardAction(component, action);
  }
}
