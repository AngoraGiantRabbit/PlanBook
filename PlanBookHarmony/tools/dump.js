// Layout dump analyzer: node tools/dump.js <file.json> [textFilter]
// - no filter: print all text nodes (text + bounds)
// - with filter: print matching text nodes' bounds (clickable ancestors marked *)
import { readFileSync } from 'node:fs';

const file = process.argv[2];
const filter = process.argv[3] ?? null;
const root = JSON.parse(readFileSync(file, 'utf8'));

const out = [];
function walk(node, depth, clickableParent, bgAbove) {
  const a = node.attributes ?? {};
  const clickable = clickableParent || a.clickable === 'true';
  const bgSelf = a.backgroundColor && a.backgroundColor !== '#00000000' && a.backgroundColor !== ''
    ? a.backgroundColor : null;
  const bg = bgSelf ?? bgAbove;
  if (a.text && a.text.length > 0) {
    out.push({ text: a.text, bounds: a.origBounds || a.bounds, clickable, depth, bg });
  }
  for (const c of node.children ?? []) {
    walk(c, depth + 1, clickable, bg);
  }
}
walk(root, 0, false, null);

for (const r of out) {
  if (filter === null || r.text.includes(filter)) {
    console.log(`${r.clickable ? '*' : ' '} [${r.bounds}]${r.bg ? ` bg=${r.bg}` : ''} ${r.text}`);
  }
}
