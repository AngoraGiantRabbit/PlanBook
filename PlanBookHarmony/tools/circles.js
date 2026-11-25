// Print Circle nodes (bounds + fill color) from a layout dump: node tools/circles.js <file.json>
import { readFileSync } from 'node:fs';
const root = JSON.parse(readFileSync(process.argv[2], 'utf8'));
const out = [];
(function walk(n) {
  const a = n.attributes ?? {};
  if (a.type === 'Circle' && a.origBounds) {
    out.push(`${a.origBounds} fill=${a.backgroundColor ?? ''}`);
  }
  for (const c of n.children ?? []) walk(c);
})(root);
console.log(out.join('\n'));
