import { client } from './generated/client.gen';
import { getPlatformStatus as requestPlatformStatus } from './generated/sdk.gen';
import type { PlatformStatus } from './generated/types.gen';

export type { PlatformStatus } from './generated/types.gen';

client.setConfig({ baseUrl: '/' });

export async function getPlatformStatus(): Promise<PlatformStatus> {
  const { data, error } = await requestPlatformStatus();

  if (error !== undefined || data === undefined) {
    throw new Error('The platform status endpoint did not return a status document.');
  }

  return data;
}
