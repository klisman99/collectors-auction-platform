# ADR-0003: Use the S3 contract and SeaweedFS for local media

- **Status:** Accepted
- **Date:** 2026-09-25

## Context

The catalog owns private normalized display images and thumbnails. PostgreSQL owns their
metadata and object keys; catalog and auction HTTP endpoints own access control and stable
URLs. The first implementation used the MinIO Java client and a MinIO server and client
pair in Docker Compose. The [MinIO server](https://github.com/minio/minio/blob/master/README.md?plain=1)
and [MinIO client](https://github.com/minio/mc) repositories are archived. The historical
container tags are no longer a maintained local-storage baseline.

The storage replacement must preserve object keys and bytes without changing the database
schema or exposing the bucket. The local stack needs a maintained image and a repeatable
single-node recovery procedure. Production storage may be another S3-compatible service.

## Decision

Use the S3 API as the catalog storage boundary. The existing catalog-owned
`CatalogImageStorage` port has an AWS SDK for Java v2 adapter for put, get and delete.
The same S3 client checks bucket health with `HeadBucket`. Endpoint, region, credentials,
path-style access and bucket are deployment settings. The SDK version is managed as one
AWS BOM outside Spring Boot's dependency management.

Use SeaweedFS `weed mini` in the local Docker Compose stack. It starts a single-node S3
endpoint, creates the configured bucket, uses configured credentials, and stores its data
in a named volume. The S3 port binds to host loopback only. The application continues to
serve media through its authorized URLs; no direct object URL is returned to clients.

The selected stable releases are SeaweedFS **4.47** and AWS SDK for Java v2 **2.55.6**.
Verified on 2026-09-25 against the [SeaweedFS release](https://github.com/seaweedfs/seaweedfs/releases/tag/4.47),
[SeaweedFS quick start](https://github.com/seaweedfs/seaweedfs#quick-start-with-weed-mini),
[official SeaweedFS Docker instructions](https://github.com/seaweedfs/seaweedfs#docker),
and [AWS SDK release](https://github.com/aws/aws-sdk-java-v2/releases/tag/2.55.6).

## Alternatives considered

- **Retain MinIO:** avoids migration now but leaves archived server and client releases
  without a maintained path. Rejected.
- **Use SeaweedFS with its native API:** ties the catalog to one server and requires a
  second SDK or transport. Rejected.
- **Use a public-cloud S3 bucket for local development:** introduces an account, network
  availability and possible cost into local tests. Deferred; the S3 contract permits it
  in a separately configured deployment.

## Consequences

SeaweedFS mini is a single-node development and proof setup, not a replicated production
deployment. Its data volume and PostgreSQL dump form one recovery set. The S3 contract
does not guarantee that every compatible server implements every AWS extension; the
adapter intentionally uses only PutObject, GetObject, DeleteObject and HeadBucket.
Endpoint and path-style settings must be reviewed when moving to a different provider.
The backend must wait for storage readiness before serving media; missing buckets and
unavailable storage report unhealthy status.

## Migration and rollback

Follow [the media migration runbook](../operations/media-storage-migration.md). Stop
writers, take a PostgreSQL dump and keep the old MinIO volume intact. Copy objects through
the S3 API with unchanged keys, verify every copied byte and the database's media keys,
then start the new backend. Do not resume writers until both private and published URLs
have been checked. Until that point rollback uses the old application image, MinIO volume
and matching database snapshot. After accepting the cutover, retain those backups under
the normal retention policy and use the SeaweedFS cold-backup procedure for new writes.
