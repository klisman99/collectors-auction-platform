package io.github.klisman99.collectorsauctionplatform.catalog;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** Catalog-owned port; object storage is always private and never exposed as a URL. */
interface CatalogImageStorage {
    void put(String key, byte[] bytes, String contentType);
    byte[] get(String key);
    void delete(String key);
}

final class MinioCatalogImageStorage implements CatalogImageStorage {
    private final MinioClient client;
    private final String bucket;

    MinioCatalogImageStorage(MinioClient client, String bucket) { this.client = client; this.bucket = bucket; }

    public void put(String key, byte[] bytes, String contentType) {
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(input, Long.valueOf(bytes.length), -1L).contentType(contentType).build());
        } catch (Exception exception) { throw CatalogStorageException.unavailable("The managed image store could not save the rendition.", exception); }
    }
    public byte[] get(String key) {
        try (InputStream input = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) { return input.readAllBytes(); }
        catch (Exception exception) { throw CatalogStorageException.unavailable("The managed image store could not read the rendition.", exception); }
    }
    public void delete(String key) {
        try { client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build()); }
        catch (Exception exception) { throw CatalogStorageException.unavailable("The managed image store could not remove the rendition.", exception); }
    }
}

final class CatalogStorageException extends RuntimeException {
    private CatalogStorageException(String message, Throwable cause) { super(message, cause); }
    static CatalogStorageException unavailable(String message, Throwable cause) { return new CatalogStorageException(message, cause); }
}
