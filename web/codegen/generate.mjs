import { resolve } from 'node:path';

import { createClient } from '@hey-api/openapi-ts';

const openApiUrl = process.env.OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs';
const destination = process.env.OPENAPI_OUTPUT
  ? resolve(process.env.OPENAPI_OUTPUT)
  : resolve(import.meta.dirname, '../src/api/generated');

await createClient({
  input: openApiUrl,
  output: {
    path: destination,
    clean: true,
    entryFile: true,
  },
  plugins: ['@hey-api/client-fetch', '@hey-api/typescript', '@hey-api/sdk'],
});
