package io.github.klisman99.collectorsauctionplatform.catalog;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(path = "/api/v1/catalog/drafts", produces = MediaType.APPLICATION_JSON_VALUE)
class CatalogController {

  private final CatalogService catalog;

  CatalogController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  @Operation(
      operationId = "listCatalogDrafts",
      summary = "List the authenticated seller's private drafts")
  List<DraftResponse> list(Principal principal) {
    return catalog.list(accountId(principal)).stream().map(this::response).toList();
  }

  @GetMapping("/{id}")
  @Operation(operationId = "getCatalogDraft", summary = "Read a private collectible draft")
  DraftResponse get(Principal principal, @PathVariable UUID id) {
    return response(catalog.get(accountId(principal), id));
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "createCatalogDraft", summary = "Create a private collectible draft")
  DraftResponse create(Principal principal, @Valid @RequestBody DraftRequestPayload input) {

    return response(catalog.create(accountId(principal), input.request()));
  }

  @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(operationId = "updateCatalogDraft", summary = "Update a private collectible draft")
  DraftResponse update(
      Principal principal, @PathVariable UUID id, @Valid @RequestBody DraftRequestPayload input) {

    return response(catalog.update(accountId(principal), id, input.request()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteCatalogDraft", summary = "Delete a private collectible draft")
  void delete(Principal principal, @PathVariable UUID id) {
    catalog.delete(accountId(principal), id);
  }

  @PostMapping(path = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      operationId = "uploadCatalogDraftImage",
      summary = "Upload and normalize a draft image")
  ImageResponse image(
      Principal principal, @PathVariable UUID id, @RequestPart("file") MultipartFile file) {

    return imageResponse(catalog.addImage(accountId(principal), id, file));
  }

  @PutMapping(path = "/{id}/images/order", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "reorderCatalogDraftImages", summary = "Set the complete image order")
  void reorder(Principal principal, @PathVariable UUID id, @RequestBody ImageOrder order) {

    catalog.reorder(accountId(principal), id, order.mediaIds());
  }

  @GetMapping("/{id}/images/{mediaId}")
  @Operation(operationId = "getCatalogDraftImage", summary = "Read a private display rendition")
  ResponseEntity<byte[]> media(
      Principal principal, @PathVariable UUID id, @PathVariable UUID mediaId) {

    UUID accountId = accountId(principal);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(catalog.mediaType(accountId, id, mediaId)))
        .body(catalog.readDisplay(accountId, id, mediaId));
  }

  @GetMapping("/{id}/images/{mediaId}/thumbnail")
  @Operation(
      operationId = "getCatalogDraftImageThumbnail",
      summary = "Read a private thumbnail rendition")
  ResponseEntity<byte[]> thumbnail(
      Principal principal, @PathVariable UUID id, @PathVariable UUID mediaId) {

    UUID accountId = accountId(principal);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(catalog.mediaType(accountId, id, mediaId)))
        .body(catalog.readThumbnail(accountId, id, mediaId));
  }

  private UUID accountId(Principal principal) {
    try {
      return UUID.fromString(principal.getName());
    } catch (IllegalArgumentException exception) {
      throw CatalogApiException.forbidden();
    }
  }

  private DraftResponse response(CollectibleItem item) {
    return new DraftResponse(
        item.id(),
        item.category(),
        item.otherCategoryLabel(),
        item.title(),
        item.description(),
        item.condition(),
        item.conditionNotes(),
        item.ownershipDeclared(),
        catalog.images(item.id()).stream().map(this::imageResponse).toList(),
        item.createdAt(),
        item.updatedAt());
  }

  private ImageResponse imageResponse(CollectibleItemMedia media) {
    String url = "/api/v1/catalog/drafts/" + media.itemId() + "/images/" + media.id();
    return new ImageResponse(
        media.id(), url, url + "/thumbnail", media.contentType(), media.sortOrder());
  }
}

record DraftRequestPayload(
    @NotNull CollectibleItem.Category category,
    String otherCategoryLabel,
    String title,
    String description,
    @NotNull CollectibleItem.Condition condition,
    String conditionNotes,
    boolean ownershipDeclared) {

  DraftRequest request() {
    return new DraftRequest(
        category,
        blankToNull(otherCategoryLabel),
        title,
        description,
        condition,
        conditionNotes,
        ownershipDeclared);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}

record ImageOrder(List<UUID> mediaIds) {}

record DraftResponse(
    UUID id,
    CollectibleItem.Category category,
    String otherCategoryLabel,
    String title,
    String description,
    CollectibleItem.Condition condition,
    String conditionNotes,
    boolean ownershipDeclared,
    List<ImageResponse> images,
    Instant createdAt,
    Instant updatedAt) {}

record ImageResponse(UUID id, String url, String thumbnailUrl, String contentType, int sortOrder) {}
