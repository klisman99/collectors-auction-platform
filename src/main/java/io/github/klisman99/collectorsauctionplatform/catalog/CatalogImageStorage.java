package io.github.klisman99.collectorsauctionplatform.catalog;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Catalog-owned port; object storage is always private and never exposed as a URL. */
interface CatalogImageStorage {

  void put(String key, byte[] bytes, String contentType);

  byte[] get(String key);

  void delete(String key);
}

final class S3CatalogImageStorage implements CatalogImageStorage {

  private final S3Client client;
  private final String bucket;

  S3CatalogImageStorage(S3Client client, String bucket) {
    this.client = client;
    this.bucket = bucket;
  }

  @Override
  public void put(String key, byte[] bytes, String contentType) {
    try {
      client.putObject(
          PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
          RequestBody.fromBytes(bytes));
    } catch (Exception exception) {
      throw CatalogStorageException.unavailable(
          "The managed image store could not save the rendition.", exception);
    }
  }

  @Override
  public byte[] get(String key) {
    try {
      return client
          .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
          .asByteArray();
    } catch (Exception exception) {
      throw CatalogStorageException.unavailable(
          "The managed image store could not read the rendition.", exception);
    }
  }

  @Override
  public void delete(String key) {
    try {
      client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    } catch (Exception exception) {
      throw CatalogStorageException.unavailable(
          "The managed image store could not remove the rendition.", exception);
    }
  }
}

final class CatalogStorageException extends RuntimeException {

  private CatalogStorageException(String message, Throwable cause) {
    super(message, cause);
  }

  static CatalogStorageException unavailable(String message, Throwable cause) {
    return new CatalogStorageException(message, cause);
  }
}
