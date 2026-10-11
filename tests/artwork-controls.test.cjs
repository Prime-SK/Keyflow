const assert = require('node:assert/strict');
const fs = require('node:fs');
const Module = require('node:module');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');
function load(relative) {
  const filename = path.resolve(__dirname, relative);
  const helper = new Module(filename, module);
  helper._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText, filename);
  return helper.exports;
}
const { artworkControls, contrastRatio, tintedBackground } = load('../lib/artwork-controls.ts');
const themes = load('../constants/player-theme.ts');
test('cover controls remain readable across saturated, pale and dark covers in both themes', () => {
  for (const [dark, base] of [[false, themes.playerThemes.light], [true, themes.playerThemes.dark]]) {
    assert.ok(base, 'theme fixture exists');
    for (let r = 0; r <= 255; r += 51) for (let g = 0; g <= 255; g += 51) for (let b = 0; b <= 255; b += 51) {
      const tint = '#' + [r, g, b].map(value => value.toString(16).padStart(2, '0')).join('');
      const result = artworkControls(base, tint, dark);
      assert.ok(contrastRatio(result.accent, tintedBackground(base.background, tint, dark ? 0.16 : 0.08)) >= 4.5);
      assert.ok(contrastRatio(result.accent, base.surface) >= 4.5);
      assert.ok(contrastRatio(result.accent, result.onAccent) >= 4.5);
    }
    assert.equal(artworkControls(base, null, dark), base);
    assert.equal(artworkControls(base, 'invalid', dark), base);
  }
});
