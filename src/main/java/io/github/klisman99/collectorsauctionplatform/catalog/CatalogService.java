package io.github.klisman99.collectorsauctionplatform.catalog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

record DraftRequest(CollectibleItem.Category category, String otherCategoryLabel, String title, String description,
                    CollectibleItem.Condition condition, String conditionNotes, boolean ownershipDeclared) {}

@Service
class CatalogService {
    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int DISPLAY_MAX_EDGE = 2_000;
    private static final int THUMBNAIL_MAX_EDGE = 400;
    private final CollectibleItemRepository items;
    private final CollectibleItemMediaRepository media;
    private final CatalogImageStorage storage;

    CatalogService(CollectibleItemRepository items, CollectibleItemMediaRepository media, CatalogImageStorage storage) {
        this.items = items; this.media = media; this.storage = storage;
    }

    @Transactional(readOnly = true) List<CollectibleItem> list(UUID owner) { return items.findAllByOwnerIdOrderByUpdatedAtDesc(owner); }
    @Transactional(readOnly = true) CollectibleItem get(UUID owner, UUID id) { return owned(owner, id); }
    @Transactional CollectibleItem create(UUID owner, DraftRequest request) { validate(request); return items.save(CollectibleItem.create(owner, request, Instant.now())); }
    @Transactional CollectibleItem update(UUID owner, UUID id, DraftRequest request) { var item = ownedForUpdate(owner, id); validate(request); item.apply(request, Instant.now()); return items.save(item); }

    @Transactional void delete(UUID owner, UUID id) { var item = ownedForUpdate(owner, id); var images = media.findAllByItemIdOrderBySortOrder(id); items.delete(item); afterCommit(() -> images.forEach(this::deleteRenditions)); }

    @Transactional
    CollectibleItemMedia addImage(UUID owner, UUID id, MultipartFile file) {
        var item = ownedForUpdate(owner, id); // serializes the image limit and next order per draft
        if (file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) throw CatalogApiException.invalidImageCount("Images must be non-empty and no larger than 5 MB.");
        List<CollectibleItemMedia> current = media.findAllByItemIdOrderBySortOrder(id);
        if (current.size() >= 5) throw CatalogApiException.invalidImageCount("A draft can contain at most five images.");
        try {
            byte[] source = file.getBytes();
            ImageFormat format = ImageFormat.from(source).orElseThrow(() -> CatalogApiException.invalidImage("Only JPEG, PNG, and WebP image bytes are accepted."));
            String declared = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(java.util.Locale.ROOT);
            if (!declared.equals(format.contentType)) throw CatalogApiException.invalidImage("The declared image type does not match the image bytes.");
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1) throw CatalogApiException.invalidImage("The image content could not be decoded.");
            Renditions renditions = Renditions.from(image);
            UUID mediaId = UUID.randomUUID(); String baseKey = item.id() + "/" + mediaId;
            String displayKey = baseKey + "/display.jpg"; String thumbnailKey = baseKey + "/thumbnail.jpg";
            storage.put(displayKey, renditions.display, "image/jpeg");
            try { storage.put(thumbnailKey, renditions.thumbnail, "image/jpeg"); }
            catch (RuntimeException exception) { storage.delete(displayKey); throw exception; }
            afterRollback(() -> { storage.delete(displayKey); storage.delete(thumbnailKey); });
            return media.save(CollectibleItemMedia.create(id, displayKey, thumbnailKey, "image/jpeg", renditions.display.length,
                    renditions.displayWidth, renditions.displayHeight, current.size(), Instant.now()));
        } catch (IOException exception) { throw CatalogApiException.invalidImage("The image could not be processed safely."); }
    }

    @Transactional void reorder(UUID owner, UUID id, List<UUID> order) {
        ownedForUpdate(owner, id); var all = media.findAllByItemIdOrderBySortOrder(id);
        if (order == null || order.size() != all.size() || new HashSet<>(order).size() != order.size() || !all.stream().map(CollectibleItemMedia::id).collect(java.util.stream.Collectors.toSet()).equals(new HashSet<>(order))) throw CatalogApiException.invalidImageOrder("The image order must contain every draft image exactly once.");
        for (int i = 0; i < all.size(); i++) all.get(i).sortOrder(-1 - i); media.flush();
        Map<UUID, CollectibleItemMedia> byId = new HashMap<>(); all.forEach(image -> byId.put(image.id(), image));
        for (int i = 0; i < order.size(); i++) byId.get(order.get(i)).sortOrder(i);
    }

    byte[] readDisplay(UUID owner, UUID itemId, UUID mediaId) { return storage.get(mediaForOwner(owner, itemId, mediaId).displayStorageKey()); }
    byte[] readThumbnail(UUID owner, UUID itemId, UUID mediaId) { return storage.get(mediaForOwner(owner, itemId, mediaId).thumbnailStorageKey()); }
    String mediaType(UUID owner, UUID itemId, UUID mediaId) { return mediaForOwner(owner, itemId, mediaId).contentType(); }
    List<CollectibleItemMedia> images(UUID id) { return media.findAllByItemIdOrderBySortOrder(id); }
    private CollectibleItem owned(UUID owner, UUID id) { return items.findById(id).filter(item -> item.ownerId().equals(owner)).orElseThrow(CatalogApiException::notFound); }
    private CollectibleItem ownedForUpdate(UUID owner, UUID id) { return items.findByIdAndOwnerId(id, owner).orElseThrow(CatalogApiException::notFound); }
    private CollectibleItemMedia mediaForOwner(UUID owner, UUID itemId, UUID mediaId) { owned(owner, itemId); return media.findById(mediaId).filter(image -> image.itemId().equals(itemId)).orElseThrow(CatalogApiException::notFound); }
    private void deleteRenditions(CollectibleItemMedia image) { storage.delete(image.displayStorageKey()); storage.delete(image.thumbnailStorageKey()); }
    private static void afterRollback(Runnable action) { TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) action.run(); } }); }
    private static void afterCommit(Runnable action) { TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { action.run(); } }); }

    private void validate(DraftRequest request) {
        if (request.category() == null || request.title() == null || request.title().trim().length() < 5 || request.title().length() > 120) throw CatalogApiException.invalid("Title must contain 5 to 120 characters.");
        if (request.description() == null || request.description().trim().length() < 20 || request.description().length() > 5000) throw CatalogApiException.invalid("Description must contain 20 to 5000 characters.");
        if (request.condition() == null || request.conditionNotes() == null || request.conditionNotes().trim().length() < 10 || request.conditionNotes().length() > 2000) throw CatalogApiException.invalid("Condition and condition notes are required.");
        if (!request.ownershipDeclared()) throw CatalogApiException.ownershipNotDeclared("You must affirm that you own this physical collectible and have the right to sell it.");
        if (request.category() == CollectibleItem.Category.OTHER && (request.otherCategoryLabel() == null || request.otherCategoryLabel().trim().isEmpty() || request.otherCategoryLabel().length() > 80)) throw CatalogApiException.otherCategory("Other category requires a short label.");
        if (request.category() != CollectibleItem.Category.OTHER && request.otherCategoryLabel() != null) throw CatalogApiException.otherCategory("Only the Other category accepts a category label.");
    }

    private enum ImageFormat { JPEG("image/jpeg"), PNG("image/png"), WEBP("image/webp"); private final String contentType; ImageFormat(String contentType) { this.contentType = contentType; }
        static Optional<ImageFormat> from(byte[] bytes) { if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return Optional.of(JPEG); if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G' && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10) return Optional.of(PNG); if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return Optional.of(WEBP); return Optional.empty(); }
    }
    private record Renditions(byte[] display, byte[] thumbnail, int displayWidth, int displayHeight) {
        static Renditions from(BufferedImage source) throws IOException { BufferedImage display = scale(source, DISPLAY_MAX_EDGE); BufferedImage thumbnail = scale(source, THUMBNAIL_MAX_EDGE); return new Renditions(jpeg(display), jpeg(thumbnail), display.getWidth(), display.getHeight()); }
        private static BufferedImage scale(BufferedImage source, int maximumEdge) { double ratio = Math.min(1d, maximumEdge / (double) Math.max(source.getWidth(), source.getHeight())); int width = Math.max(1, (int) Math.round(source.getWidth() * ratio)); int height = Math.max(1, (int) Math.round(source.getHeight() * ratio)); BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); Graphics2D graphics = result.createGraphics(); graphics.setComposite(AlphaComposite.SrcOver); graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height); graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC); graphics.drawImage(source, 0, 0, width, height, null); graphics.dispose(); return result; }
        private static byte[] jpeg(BufferedImage image) throws IOException { ByteArrayOutputStream output = new ByteArrayOutputStream(); if (!ImageIO.write(image, "jpeg", output)) throw new IOException("JPEG writer unavailable"); return output.toByteArray(); }
    }
}
