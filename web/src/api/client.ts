import createClient from 'openapi-fetch';

import type { components, paths } from './generated';

export type PlatformStatus = components['schemas']['PlatformStatus'];

const client = createClient<paths>({ baseUrl: '/' });

export async function getPlatformStatus(): Promise<PlatformStatus> {
  const { data, error } = await client.GET('/api/v1/status');

  if (error !== undefined || data === undefined) {
    throw new Error('The platform status endpoint did not return a status document.');
  }

  return data;
}
