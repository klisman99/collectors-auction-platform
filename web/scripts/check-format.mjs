import { readFile, readdir } from 'node:fs/promises';
import { join, resolve } from 'node:path';

const files = ['index.html', 'package.json', 'vite.config.ts', 'tsconfig.json', 'tsconfig.app.json', 'tsconfig.node.json'];
const webRoot = resolve(import.meta.dirname, '..');

async function sourceFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const discovered = [];

  for (const entry of entries) {
    const absolutePath = join(directory, entry.name);
    if (entry.isDirectory()) {
      discovered.push(...(await sourceFiles(absolutePath)));
    } else if (/\.(css|ts|tsx)$/.test(entry.name)) {
      discovered.push(absolutePath);
    }
  }

  return discovered;
}

for (const file of await sourceFiles(join(webRoot, 'src'))) {
  files.push(file);
}

const violations = [];
for (const file of files) {
  const absolutePath = file.startsWith(webRoot) ? file : join(webRoot, file);
  const content = await readFile(absolutePath, 'utf8');
  if (!content.endsWith('\n')) {
    violations.push(`${absolutePath}: missing final newline`);
  }
  content.split('\n').forEach((line, index) => {
    if (/\s+$/.test(line)) {
      violations.push(`${absolutePath}:${index + 1}: trailing whitespace`);
    }
  });
}

if (violations.length > 0) {
  throw new Error(`Formatting convention violations:\n${violations.join('\n')}`);
}
