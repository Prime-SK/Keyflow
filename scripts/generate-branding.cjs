// Render the editable vector masters. No packages are added to the app.
// Set KEYFLOW_ASSET_NODE_MODULES to a directory containing sharp if not installed locally.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require(process.env.KEYFLOW_ASSET_NODE_MODULES
  ? path.join(process.env.KEYFLOW_ASSET_NODE_MODULES, 'sharp') : 'sharp');
const root = path.join(__dirname, '..', 'assets', 'branding');
const mark = `<rect x="16" y="16" width="22" height="96" rx="5"/>
<g stroke="currentColor" stroke-width="6" stroke-linejoin="round">
<path d="M48 40 L80 18 H108 L72 49 Z"/>
<path d="M48 55 L73 69 L48 83 Z"/>
<path d="M48 96 L70 85 L108 110 H80 Z"/></g>`;
function svg(width, height, body) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><title>Keyflow Play K</title>${body}</svg>`;
}
function symbol(color, x = 0, y = 0, size = 128) {
  return `<g fill="${color}" color="${color}" transform="translate(${x} ${y}) scale(${size / 128})">${mark}</g>`;
}
async function render(name, source) {
  fs.writeFileSync(path.join(root, `${name}.svg`), source + '\n');
  await sharp(Buffer.from(source)).png().toFile(path.join(root, `${name}.png`));
}
async function main() {
  await render('keyflow-symbol', svg(128, 128, symbol('#1E40AF')));
  // Android masks require generous clear space around the foreground mark.
  await render('keyflow-adaptive-foreground', svg(1024, 1024, symbol('#6D91FF', 192, 192, 640)));
  await render('keyflow-monochrome', svg(1024, 1024, symbol('#FFFFFF', 192, 192, 640)));
  await render('keyflow-icon', svg(1024, 1024, '<rect width="1024" height="1024" rx="224" fill="#0B1020"/>' + symbol('#6D91FF', 128, 128, 768)));
  for (const [name, color] of [['keyflow-logo', '#1E40AF'], ['keyflow-logo-dark', '#6D91FF']]) {
    await render(name, svg(540, 160, symbol(color, 0, 16) + `<text x="120" y="128" fill="${color}" font-family="Segoe UI, sans-serif" font-size="120" font-weight="700" letter-spacing="-3">eyflow</text>`));
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
