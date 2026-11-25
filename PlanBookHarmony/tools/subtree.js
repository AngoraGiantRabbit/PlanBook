// Print subtree of the first node whose text contains the filter: node tools/subtree.js <file.json> <textFilter>
import { readFileSync } from 'node:fs';
const root = JSON.parse(readFileSync(process.argv[2], 'utf8'));
const filter = process.argv[3];
let found = null;
(function walk(n) {
  const a = n.attributes ?? {};
  if (!found && a.text && a.text.includes(filter)) found = n;
  for (const c of n.children ?? []) walk(c);
})(root);
if (!found) { console.log('NOT FOUND'); process.exit(0); }
function dump(n, d) {
  const a = n.attributes ?? {};
  const info = [a.type, a.origBounds || a.bounds, a.text ? `text=${a.text}` : '', a.backgroundColor ? `bg=${a.backgroundColor}` : '', a.clickable === 'true' ? 'CLICKABLE' : ''].filter(Boolean).join(' ');
  console.log('  '.repeat(d) + info);
  for (const c of n.children ?? []) dump(c, d + 1);
}
let p = found; let depth = 0;
while (p) { p = p.__parent; }
dump(found, 0);
