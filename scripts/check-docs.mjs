import { existsSync } from 'node:fs';
import { readFile, readdir } from 'node:fs/promises';
import { dirname, extname, join, resolve } from 'node:path';

const repositoryRoot = resolve(import.meta.dirname, '..');
const ignoredDirectories = new Set(['.git', 'node_modules', 'target', 'dist']);

async function markdownFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    if (ignoredDirectories.has(entry.name)) {
      continue;
    }

    const absolutePath = join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await markdownFiles(absolutePath)));
    } else if (extname(entry.name).toLowerCase() === '.md') {
      files.push(absolutePath);
    }
  }

  return files;
}

const failures = [];
for (const file of await markdownFiles(repositoryRoot)) {
  const content = await readFile(file, 'utf8');
  const links = content.matchAll(/\[[^\]]*\]\(([^)]+)\)/g);

  for (const match of links) {
    const rawTarget = match[1].trim().replace(/^<|>$/g, '');
    if (/^(https?:|mailto:|#)/.test(rawTarget)) {
      continue;
    }

    const localTarget = decodeURIComponent(rawTarget.split('#', 1)[0]);
    if (localTarget && !existsSync(resolve(dirname(file), localTarget))) {
      failures.push(`${file}: broken local link ${rawTarget}`);
    }
  }
}

if (failures.length > 0) {
  throw new Error(`Documentation validation failed:\n${failures.join('\n')}`);
}

console.log('Documentation links are valid.');
