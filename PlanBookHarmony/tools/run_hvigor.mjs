// 绕过 hvigorw.bat 直接驱动 hvigorw.js：
// 1) 在 node 进程内把 JBR（无空格 junction）注入 PATH（真 Windows 反斜杠形式），
//    保证打包任务 spawn java 可达（.bat 路线在本 shell 下会被带空格/正斜杠 PATH 卡住）；
// 2) 重写 process.argv 以符合 hvigorw.js 的预期。
import { pathToFileURL } from 'node:url';

const deveco = process.env.DEVECO ?? 'C:/Program Files/Huawei/DevEco Studio';
const jbr = process.env.HM_JBR ?? 'C:/hm-jbr';

process.env.JAVA_HOME = jbr;
process.env.PATH = `${jbr}\\bin;${process.env.PATH ?? ''}`;
if (!process.env.DEVECO_SDK_HOME) {
  process.env.DEVECO_SDK_HOME = `${deveco}/sdk`;
}
process.argv = [process.argv[0], `${deveco}/tools/hvigor/bin/hvigorw.js`, ...process.argv.slice(2)];

await import(pathToFileURL(`${deveco}/tools/hvigor/bin/hvigorw.js`).href);
