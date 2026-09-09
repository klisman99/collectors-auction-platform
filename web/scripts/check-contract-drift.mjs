import { mkdtemp, readFile, readdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, relative, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const webRoot = resolve(import.meta.dirname, '..');
const expectedRoot = join(webRoot, 'src/api/generated');
const temporaryRoot = await mkdtemp(join(tmpdir(), 'collectors-openapi-'));
const actualRoot = join(temporaryRoot, 'generated');

async function files(root, current = root) {
  const entries = await readdir(current, { withFileTypes: true });
  const paths = await Promise.all(
    entries.map(async (entry) => {
      const path = join(current, entry.name);
      return entry.isDirectory() ? files(root, path) : [relative(root, path)];
    }),
  );

  return paths.flat().sort();
}

function firstDifference(expected, actual) {
  const expectedLines = expected.toString().split('\n');
  const actualLines = actual.toString().split('\n');
  const length = Math.max(expectedLines.length, actualLines.length);

  for (let index = 0; index < length; index += 1) {
    if (expectedLines[index] !== actualLines[index]) {
      return `[DEBUG-142c] line ${index + 1}: expected ${JSON.stringify(expectedLines[index] ?? '<EOF>')}, received ${JSON.stringify(actualLines[index] ?? '<EOF>')}`;
    }
  }

  return '[DEBUG-142c] files differ at byte level.';
}

try {
  const generation = spawnSync(process.execPath, [join(webRoot, 'codegen/generate.mjs')], {
    cwd: webRoot,
    env: { ...process.env, OPENAPI_OUTPUT: actualRoot },
    stdio: 'inherit',
  });

  if (generation.status !== 0) {
    throw new Error(`OpenAPI generation failed with exit code ${generation.status ?? 'unknown'}.`);
  }

  const expectedFiles = await files(expectedRoot);
  const actualFiles = await files(actualRoot);

  if (JSON.stringify(expectedFiles) !== JSON.stringify(actualFiles)) {
    throw new Error(
      `Generated OpenAPI file set is stale. Expected ${expectedFiles.join(', ')}, received ${actualFiles.join(', ')}.`,
    );
  }

  for (const file of expectedFiles) {
    const [expected, actual] = await Promise.all([
      readFile(join(expectedRoot, file)),
      readFile(join(actualRoot, file)),
    ]);

    if (!expected.equals(actual)) {
      throw new Error(
        `Generated OpenAPI file is stale: ${file}. ${firstDifference(expected, actual)}`,
      );
    }
  }

  console.log('Generated OpenAPI client matches the live backend contract.');
} finally {
  await rm(temporaryRoot, { recursive: true, force: true });
}
