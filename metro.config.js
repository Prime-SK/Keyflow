const path = require('path');
const { getDefaultConfig } = require('expo/metro-config');
const { FileStore } = require('metro-cache');

const config = getDefaultConfig(__dirname);
// Release bundling resets its cache; keep it separate from a running dev server.
const cacheVariant = process.env.NODE_ENV === 'production' ? 'production' : 'development';
config.cacheStores = [new FileStore({ root: path.join(__dirname, '.tools', 'metro-cache', cacheVariant) })];
// Workspace-local Android tools contain rapidly changing Gradle cache files.
const escapeRegex = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
// Metro's Windows watcher normalizes to '/', while its file map uses '\\'.
const portablePath = (value) => value.split(/[/\\]/).map(escapeRegex).join('[/\\\\]');
const ignoredRoots = ['.tools', 'artifacts'].map((folder) =>
  new RegExp(`^${portablePath(path.join(__dirname, folder))}(?:[/\\\\].*)?$`)
);
const existing = config.resolver.blockList;
config.resolver.blockList = [
  ...(Array.isArray(existing) ? existing : existing ? [existing] : []),
  ...ignoredRoots,
];

module.exports = config;
