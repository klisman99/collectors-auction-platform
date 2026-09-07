package io.github.klisman99.collectorsauctionplatform.catalog;

import io.github.klisman99.collectorsauctionplatform.identity.AccountEligibility;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

record DraftRequest(CollectibleItem.Category category, String otherCategoryLabel, String title, String description,
                    CollectibleItem.Condition condition, String conditionNotes, boolean ownershipDeclared) {}

@Service
class CatalogService {
    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private final CollectibleItemRepository items; private final CollectibleItemMediaRepository media; private final AccountEligibility eligibility;
    private final Path storage = Path.of(System.getProperty("java.io.tmpdir"), "collectors-auction-images");
    CatalogService(CollectibleItemRepository items, CollectibleItemMediaRepository media, AccountEligibility eligibility) { this.items=items; this.media=media; this.eligibility=eligibility; }
    @Transactional(readOnly=true) List<CollectibleItem> list(UUID owner) { ensureEligible(owner); return items.findAllByOwnerIdOrderByUpdatedAtDesc(owner); }
    @Transactional(readOnly=true) CollectibleItem get(UUID owner, UUID id) { ensureEligible(owner); return owned(owner,id); }
    @Transactional CollectibleItem create(UUID owner, DraftRequest request) { ensureEligible(owner); validate(request); return items.save(CollectibleItem.create(owner,request,Instant.now())); }
    @Transactional CollectibleItem update(UUID owner, UUID id, DraftRequest request) { ensureEligible(owner); var item=owned(owner,id); validate(request); item.apply(request,Instant.now()); return items.save(item); }
    @Transactional void delete(UUID owner, UUID id) { ensureEligible(owner); var item=owned(owner,id); media.findAllByItemIdOrderBySortOrder(id).forEach(m -> deleteFile(m.storageKey())); items.delete(item); }
    @Transactional CollectibleItemMedia addImage(UUID owner, UUID id, MultipartFile file) {
        ensureEligible(owner); owned(owner,id); if(file.isEmpty() || file.getSize()>MAX_IMAGE_BYTES) throw CatalogApiException.invalidImage("Images must be non-empty and no larger than 5 MB.");
        try { byte[] bytes=file.getBytes(); BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes)); if(image==null) throw CatalogApiException.invalidImage("The image content could not be decoded.");
            String declaredType = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
            String format = bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P' ? "webp"
                    : bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G' ? "png"
                    : bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff ? "jpeg" : "";
            String type = switch (format) { case "png" -> "image/png"; case "webp" -> "image/webp"; default -> "image/jpeg"; };
            if (!declaredType.equals(type)) throw CatalogApiException.invalidImage("The declared image type does not match the decoded bytes.");
            var current=media.findAllByItemIdOrderBySortOrder(id); if(current.size()>=5) throw CatalogApiException.invalidImage("A draft can contain at most five images.");
            String key=id+"/"+UUID.randomUUID()+("image/png".equals(type)?".png":".jpg"); Files.createDirectories(storage.resolve(id.toString()));
            // ImageIO decodes pixels and writes a fresh image, removing metadata and trusting no client bytes.
            String outputType="image/png".equals(type)?"png":"jpg"; Path path=storage.resolve(key); Files.createDirectories(path.getParent()); ImageIO.write(image,outputType,path.toFile());
            var result=media.save(CollectibleItemMedia.create(id,key,type,Files.size(path),image.getWidth(),image.getHeight(),current.size(),Instant.now())); return result;
        } catch (IOException e) { throw CatalogApiException.invalidImage("The image could not be processed safely."); }
    }
    @Transactional void reorder(UUID owner, UUID id, List<UUID> order) { ensureEligible(owner); owned(owner,id); var all=media.findAllByItemIdOrderBySortOrder(id); if(order.size()!=all.size() || new HashSet<>(order).size()!=order.size() || !all.stream().map(CollectibleItemMedia::id).collect(java.util.stream.Collectors.toSet()).equals(new HashSet<>(order))) throw CatalogApiException.invalidImage("The image order must contain every draft image exactly once."); var byId=new HashMap<UUID,CollectibleItemMedia>(); all.forEach(m->byId.put(m.id(),m)); for(int i=0;i<order.size();i++) byId.get(order.get(i)).sortOrder(i); }
    byte[] readMedia(UUID owner, UUID itemId, UUID mediaId) { owned(owner,itemId); var m=media.findById(mediaId).filter(x->x.itemId().equals(itemId)).orElseThrow(CatalogApiException::notFound); try{return Files.readAllBytes(storage.resolve(m.storageKey()));}catch(IOException e){throw CatalogApiException.notFound();} }
    String mediaType(UUID owner, UUID itemId, UUID mediaId) { owned(owner,itemId); return media.findById(mediaId).map(CollectibleItemMedia::contentType).orElseThrow(CatalogApiException::notFound); }
    List<CollectibleItemMedia> images(UUID id){return media.findAllByItemIdOrderBySortOrder(id);}
    private CollectibleItem owned(UUID owner, UUID id){return items.findById(id).filter(x->x.ownerId().equals(owner)).orElseThrow(CatalogApiException::notFound);}
    private void ensureEligible(UUID owner){if(!eligibility.canEditCatalog(owner))throw CatalogApiException.forbidden();}
    private void validate(DraftRequest r){if(r.category()==null||r.title()==null||r.title().trim().length()<5||r.title().length()>120)throw CatalogApiException.invalid("Title must contain 5 to 120 characters."); if(r.description()==null||r.description().trim().length()<20||r.description().length()>5000)throw CatalogApiException.invalid("Description must contain 20 to 5000 characters."); if(r.condition()==null||r.conditionNotes()==null||r.conditionNotes().trim().length()<10||r.conditionNotes().length()>2000)throw CatalogApiException.invalid("Condition and condition notes are required."); if(!r.ownershipDeclared())throw CatalogApiException.invalid("Ownership declaration must be accepted."); if(r.category()==CollectibleItem.Category.OTHER && (r.otherCategoryLabel()==null||r.otherCategoryLabel().trim().isEmpty()||r.otherCategoryLabel().length()>80))throw CatalogApiException.invalid("Other category requires a short label."); if(r.category()!=CollectibleItem.Category.OTHER && r.otherCategoryLabel()!=null)throw CatalogApiException.invalid("Only the Other category accepts a category label.");}
    private void deleteFile(String key){try{Files.deleteIfExists(storage.resolve(key));}catch(IOException ignored){}}
}
