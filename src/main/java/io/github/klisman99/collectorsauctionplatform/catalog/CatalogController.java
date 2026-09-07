package io.github.klisman99.collectorsauctionplatform.catalog;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(path="/api/v1/catalog/drafts", produces=MediaType.APPLICATION_JSON_VALUE)
class CatalogController {
    private final CatalogService catalog;
    CatalogController(CatalogService catalog){this.catalog=catalog;}
    @GetMapping @Operation(operationId="listCatalogDrafts", summary="List the authenticated seller's private drafts") List<DraftResponse> list(Principal principal){return catalog.list(account(principal)).stream().map(this::response).toList();}
    @GetMapping("/{id}") @Operation(operationId="getCatalogDraft", summary="Read a private collectible draft") DraftResponse get(Principal p,@PathVariable UUID id){return response(catalog.get(account(p),id));}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE) @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId="createCatalogDraft", summary="Create a private collectible draft") DraftResponse create(Principal p,@Valid @RequestBody DraftRequestPayload input){return response(catalog.create(account(p),input.request()));}
    @PutMapping(path="/{id}",consumes=MediaType.APPLICATION_JSON_VALUE) @Operation(operationId="updateCatalogDraft", summary="Update a private collectible draft") DraftResponse update(Principal p,@PathVariable UUID id,@Valid @RequestBody DraftRequestPayload input){return response(catalog.update(account(p),id,input.request()));}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(operationId="deleteCatalogDraft", summary="Delete a private collectible draft") void delete(Principal p,@PathVariable UUID id){catalog.delete(account(p),id);}
    @PostMapping(path="/{id}/images", consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @Operation(operationId="uploadCatalogDraftImage", summary="Upload and normalize a draft image") ImageResponse image(Principal p,@PathVariable UUID id,@RequestPart("file") MultipartFile file){return imageResponse(catalog.addImage(account(p),id,file));}
    @PutMapping(path="/{id}/images/order", consumes=MediaType.APPLICATION_JSON_VALUE) @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(operationId="reorderCatalogDraftImages", summary="Set the complete image order") void reorder(Principal p,@PathVariable UUID id,@RequestBody ImageOrder order){catalog.reorder(account(p),id,order.mediaIds());}
    @GetMapping("/{id}/images/{mediaId}") @Operation(operationId="getCatalogDraftImage", summary="Read a private display rendition") ResponseEntity<byte[]> media(Principal p,@PathVariable UUID id,@PathVariable UUID mediaId){return ResponseEntity.ok().contentType(MediaType.parseMediaType(catalog.mediaType(account(p),id,mediaId))).body(catalog.readDisplay(account(p),id,mediaId));}
    @GetMapping("/{id}/images/{mediaId}/thumbnail") @Operation(operationId="getCatalogDraftImageThumbnail", summary="Read a private thumbnail rendition") ResponseEntity<byte[]> thumbnail(Principal p,@PathVariable UUID id,@PathVariable UUID mediaId){return ResponseEntity.ok().contentType(MediaType.parseMediaType(catalog.mediaType(account(p),id,mediaId))).body(catalog.readThumbnail(account(p),id,mediaId));}
    private UUID account(Principal p){try{return UUID.fromString(p.getName());}catch(Exception e){throw CatalogApiException.forbidden();}}
    private DraftResponse response(CollectibleItem i){return new DraftResponse(i.id(),i.category(),i.otherCategoryLabel(),i.title(),i.description(),i.condition(),i.conditionNotes(),i.ownershipDeclared(),catalog.images(i.id()).stream().map(this::imageResponse).toList(),i.createdAt(),i.updatedAt());}
    private ImageResponse imageResponse(CollectibleItemMedia m){String url="/api/v1/catalog/drafts/"+m.itemId()+"/images/"+m.id(); return new ImageResponse(m.id(),url,url+"/thumbnail",m.contentType(),m.sortOrder());}
}

record DraftRequestPayload(@NotNull CollectibleItem.Category category, String otherCategoryLabel, String title, String description,
                           @NotNull CollectibleItem.Condition condition, String conditionNotes, boolean ownershipDeclared){DraftRequest request(){return new DraftRequest(category,blankToNull(otherCategoryLabel),title,description,condition,conditionNotes,ownershipDeclared);} private static String blankToNull(String s){return s==null||s.isBlank()?null:s.trim();}}
record ImageOrder(List<UUID> mediaIds){}
record DraftResponse(UUID id,CollectibleItem.Category category,String otherCategoryLabel,String title,String description,CollectibleItem.Condition condition,String conditionNotes,boolean ownershipDeclared,List<ImageResponse> images,java.time.Instant createdAt,java.time.Instant updatedAt){}
record ImageResponse(UUID id,String url,String thumbnailUrl,String contentType,int sortOrder){}
