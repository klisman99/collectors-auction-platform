# Migrating existing private media

The migration copies objects through S3. SeaweedFS cannot read a MinIO data volume as
its own data directory. PostgreSQL remains authoritative for media rows and object keys;
no schema or URL rewrite is needed. Perform this procedure in a maintenance window and
keep the old stack and volume until verification succeeds.

1. From the old revision, stop writers and take a matching database and object backup:

   ```bash
   umask 077
   mkdir -p backups/media-cutover
   docker compose stop web backend minio-init minio
   docker compose exec -T postgres pg_dump -U collectors -d collectors_auction -Fc \
     > backups/media-cutover/database.dump
   old_minio=$(docker compose ps --all --quiet minio)
   docker cp "$old_minio:/data/." - > backups/media-cutover/minio.tar
   sha256sum backups/media-cutover/database.dump backups/media-cutover/minio.tar \
     > backups/media-cutover/SHA256SUMS
   docker compose start minio
   ```

   Keep web and backend stopped through verification. Record the old application image,
   revision and bucket name. Keep both backups private. Never run
   `docker compose down --volumes` on the source stack.
2. From the new revision, start only SeaweedFS with `docker compose up -d seaweedfs`.
   It creates a separate named volume and the configured bucket; the old MinIO volume
   remains untouched. Set the destination credentials in the new stack. Confirm its
   `HeadBucket` operation succeeds before copying.
3. Install the official AWS CLI on the operator host. Set `OLD_S3_ENDPOINT`,
   `OLD_S3_ACCESS_KEY`, `OLD_S3_SECRET_KEY`, `NEW_S3_ENDPOINT`, `NEW_S3_ACCESS_KEY`,
   `NEW_S3_SECRET_KEY`, and `S3_BUCKET` in the operator's private environment. Endpoints
   must be reachable from that host. Run the following from a private filesystem with
   enough free space for two copies of all objects:

   ```bash
   umask 077
   migration_dir=$(mktemp -d)
   mkdir -p "$migration_dir/source" "$migration_dir/verified"
   AWS_ACCESS_KEY_ID="$OLD_S3_ACCESS_KEY" AWS_SECRET_ACCESS_KEY="$OLD_S3_SECRET_KEY" \
     aws --region us-east-1 --endpoint-url "$OLD_S3_ENDPOINT" \
     s3 sync "s3://$S3_BUCKET/" "$migration_dir/source/"
   AWS_ACCESS_KEY_ID="$NEW_S3_ACCESS_KEY" AWS_SECRET_ACCESS_KEY="$NEW_S3_SECRET_KEY" \
     aws --region us-east-1 --endpoint-url "$NEW_S3_ENDPOINT" \
     s3 sync "$migration_dir/source/" "s3://$S3_BUCKET/" --content-type image/jpeg
   AWS_ACCESS_KEY_ID="$NEW_S3_ACCESS_KEY" AWS_SECRET_ACCESS_KEY="$NEW_S3_SECRET_KEY" \
     aws --region us-east-1 --endpoint-url "$NEW_S3_ENDPOINT" \
     s3 sync "s3://$S3_BUCKET/" "$migration_dir/verified/"
   diff -qr "$migration_dir/source" "$migration_dir/verified"
   ```

4. Confirm every authoritative media key exists in the copied set. Run the query against
   the stopped source database (or its restored snapshot):

   ```bash
   docker compose exec -T postgres psql -X -qAt -U collectors -d collectors_auction \
     -c 'SELECT display_storage_key FROM collectible_item_media UNION ALL SELECT thumbnail_storage_key FROM collectible_item_media' \
     > "$migration_dir/media-keys.txt"
   while IFS= read -r key; do
     test -f "$migration_dir/verified/$key" || { echo "Missing media key: $key" >&2; exit 1; }
   done < "$migration_dir/media-keys.txt"
   ```

   Check any older or orphaned objects separately; the byte comparison above covers all
   objects in the source bucket. Check a private draft image as its owner and a published
   image through its existing platform URL. An unauthenticated direct S3 request must not
   return an object.
5. Start the new backend and web processes with `docker compose up -d backend web` and
   confirm readiness and the media checks. Leave the old MinIO volume untouched.
   Only then resume writes. Remove the private temporary directory using the operator's
   approved secure cleanup process after the migration is accepted.

If any check fails before writes resume, stop the new backend and web processes, restart
the old application and MinIO with the untouched source volume and matching PostgreSQL
snapshot, and investigate. Once the new stack has accepted writes, do not roll back to
the stale snapshot without reconciling those writes. Use the new cold-backup procedure
after cutover.
