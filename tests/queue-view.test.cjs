const assert = require('node:assert/strict');
const fs = require('node:fs');
const Module = require('node:module');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');
const filename = path.resolve(__dirname, '../lib/queue-view.ts');
const helper = new Module(filename, module);
helper._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText, filename);
const { moveQueueWindow, reconcileQueueWindow, removeQueueWindow } = helper.exports;

test('display reorder preserves duplicate occurrences and global indices in a paged window', () => {
  const items = ['a', 'b', 'c'].map((id, i) => ({ id, index: 300 + i, uri: 'same-song.mp3' }));
  const next = moveQueueWindow(items, 'c', -2);
  assert.deepEqual(next.map(item => item.id), ['c', 'a', 'b']);
  assert.deepEqual(next.map(item => item.index), [300, 301, 302]);
  assert.deepEqual(items.map(item => item.id), ['a', 'b', 'c']);
  assert.deepEqual(moveQueueWindow(next, 'c', 2).map(item => item.id), ['a', 'b', 'c']);
  assert.equal(moveQueueWindow(items, 'missing', 1), items);
  assert.equal(moveQueueWindow(items, 'a', -9), items);
});

test('native confirmation reuses unchanged rows but retains metadata and priority changes', () => {
  const old = ['a', 'b', 'c'].map((id, index) => ({ id, index, title: id, artist: null, trackId: null, priority: false }));
  assert.equal(reconcileQueueWindow(old, old.map(item => ({ ...item }))), old);
  const changed = reconcileQueueWindow(old, old.map(item => item.id === 'b' ? { ...item, title: 'Corrected title', priority: true } : { ...item }));
  assert.equal(changed[0], old[0]); assert.equal(changed[2], old[2]);
  assert.equal(changed[1].title, 'Corrected title'); assert.equal(changed[1].priority, true);
  assert.deepEqual(reconcileQueueWindow(old, [old[2], old[0]]).map(item => item.id), ['c', 'a']);
});

test('swipe presentation removes only one duplicate occurrence and closes the paged index gap', () => {
  const items = ['a', 'b', 'c'].map((id, i) => ({ id, index: 300 + i, title: 'Same song' }));
  const next = removeQueueWindow(items, 'b');
  assert.deepEqual(next.map(item => [item.id, item.index]), [['a', 300], ['c', 301]]);
  assert.equal(next[0], items[0]); assert.equal(items.length, 3);
  assert.equal(removeQueueWindow(items, 'missing'), items);
  assert.deepEqual(removeQueueWindow([items[0]], 'a'), []);
});
