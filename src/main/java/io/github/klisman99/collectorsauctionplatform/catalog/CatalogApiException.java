package io.github.klisman99.collectorsauctionplatform.catalog;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class CatalogApiException extends ErrorResponseException {
    private CatalogApiException(HttpStatus status, String code, String rule, String detail) { super(status, problem(status, code, rule, detail), null); }
    static CatalogApiException invalid(String detail) { return new CatalogApiException(HttpStatus.BAD_REQUEST, "ITEM_VALIDATION_FAILED", "BR-ITEM-004", detail); }
    static CatalogApiException forbidden() { return new CatalogApiException(HttpStatus.FORBIDDEN, "CATALOG_MUTATION_FORBIDDEN", "BR-AUTH-004", "A verified active account is required to change collectible drafts."); }
    static CatalogApiException notFound() { return new CatalogApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", null, "The collectible draft was not found."); }
    static CatalogApiException invalidImage(String detail) { return new CatalogApiException(HttpStatus.BAD_REQUEST, "IMAGE_INVALID", "BR-ITEM-007", detail); }
    private static ProblemDetail problem(HttpStatus s, String c, String r, String d) { var p=ProblemDetail.forStatusAndDetail(s,d); p.setType(URI.create("urn:collectors-auction-platform:problem:"+c.toLowerCase(Locale.ROOT))); p.setProperty("code",c); p.setProperty("ruleId",r); p.setProperty("fieldErrors", List.of()); return p; }
}
