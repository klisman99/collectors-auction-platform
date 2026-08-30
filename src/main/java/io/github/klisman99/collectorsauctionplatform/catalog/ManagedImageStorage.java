package io.github.klisman99.collectorsauctionplatform.catalog;

/**
 * Catalog-owned port for managed original images and generated renditions.
 *
 * <p>No marketplace media is persisted in the baseline. Future catalog commands depend on this port instead of
 * accessing MinIO directly.
 */
public interface ManagedImageStorage {
}
