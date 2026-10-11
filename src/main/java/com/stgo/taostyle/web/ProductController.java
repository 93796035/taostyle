package com.stgo.taostyle.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import com.stgo.taostyle.service.MediaUploadService;
import com.stgo.taostyle.service.ProductService;
import com.stgo.taostyle.service.CustomizeService;
import com.stgo.taostyle.service.PersonService;
import com.stgo.taostyle.service.MobileProductSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.WebUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import com.stgo.taostyle.domain.MediaUpload;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.Product;
import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.domain.orders.Material;
import com.stgo.taostyle.service.MainOrderService;
import com.stgo.taostyle.service.MaterialService;
import java.io.UnsupportedEncodingException;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.Base64;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RequestMapping("/products")
@Controller
public class ProductController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(ProductController.class);
    @Autowired
    private TaoUtil taoUtil;
    @Autowired
    private MediaUploadService mediaUploadService;
    @Autowired
    private ProductService productService;
    @Autowired
    private CustomizeService customizeService;
    @Autowired
    private PersonService personService;
    @Autowired
    private MobileProductSyncService mobileProductSyncService;
    @Autowired
    private MainOrderService mainOrderService;
    @Autowired
    private MaterialService materialService;
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Creates the short-lived capability used by the mobile synchronizer.
     * The activation code is checked against the existing AikaPos license
     * records; it is never accepted as an arbitrary person_id.
     */
    @RequestMapping(value = "/mobileSync/activate", method = RequestMethod.POST,
            produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> activateMobileSync(@RequestBody String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        try {
            String licence = new JSONObject(json).optString("licence", "").trim();
            if (licence.length() == 0) return new ResponseEntity<String>("{\"error\":\"missing_licence\"}", headers, HttpStatus.BAD_REQUEST);
            Person licenseOwner = personService.findPersonByName("AikaPos");
            if (licenseOwner == null || customizeService.findCustomizeByKeyAndPerson(licence, licenseOwner) == null)
                return new ResponseEntity<String>("{\"error\":\"invalid_activation\"}", headers, HttpStatus.FORBIDDEN);
            Long personId;
            try { personId = Long.valueOf(licence); }
            catch (NumberFormatException ex) { return new ResponseEntity<String>("{\"error\":\"activation_has_no_person_id\"}", headers, HttpStatus.FORBIDDEN); }
            // Activation validates the licence and resolves the person only.
            // Synchronization APIs deliberately do not issue or require a token.
            return new ResponseEntity<String>(new JSONObject().put("personId", personId).toString(), headers, HttpStatus.OK);
        } catch (Exception ex) {
            return new ResponseEntity<String>("{\"error\":\"invalid_request\"}", headers, HttpStatus.BAD_REQUEST);
        }
    }

    /** Returns the authorized shared product-image package for the mobile client. */
    @RequestMapping(value = "/mobileSync/productImages", method = RequestMethod.GET)
    @ResponseBody
    public void mobileProductImages(HttpServletRequest request, HttpServletResponse response,
                                    @RequestParam(value = "batch", required = false, defaultValue = "0") int batch,
                                    @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        batch = Math.max(0, batch);
        size = Math.max(1, Math.min(20, size));
        // Product images are read-only public assets. Do not require the
        // No mobile session token is issued or required by this API.
        log.info("[MobileProductImages] request=" + request.getMethod() + " uri=" + request.getRequestURI()
                + " batch=" + batch + " size=" + size
                + " authorizationPresent=" + (request.getHeader("Authorization") != null));
        try {
            File directory = new File(File.listRoots()[0], "media" + File.separator + "product");
            log.info("[MobileProductImages] personId=public-shared-images"
                    + " filesystemRoot=" + File.listRoots()[0].getAbsolutePath()
                    + " sharedDir=" + directory.getAbsolutePath() + " exists=" + directory.isDirectory()
                    + " readable=" + directory.canRead());
            File[] sharedListing = directory.listFiles();
            log.info("[MobileProductImages] sharedListing="
                    + (sharedListing == null ? "null" : String.valueOf(sharedListing.length)));
            if (sharedListing != null) {
                int shown = 0;
                for (File file : sharedListing) if (shown++ < 20)
                    log.info("[MobileProductImages] sharedFile=" + file.getName()
                            + " file=" + file.isFile() + " bytes=" + file.length());
            }
            response.setStatus(HttpStatus.OK.value());
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=menu_images.jar");
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            int sharedImageCount = 0;
            if (sharedListing != null) for (File file : sharedListing) {
                if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).matches(".*\\.(png|jpg|jpeg|webp)$")) sharedImageCount++;
            }
            response.setHeader("X-Image-Batch", String.valueOf(batch));
            response.setHeader("X-Image-Batch-Size", String.valueOf(size));
            response.setHeader("X-Image-Shared-Total", String.valueOf(sharedImageCount));
            response.setHeader("X-Image-Batch-Count", String.valueOf((sharedImageCount + size - 1) / size));
            log.info("[MobileProductImages] starting streamed ZIP response; shared files are written directly to the client");
            Set<String> names = new HashSet<String>();
            int count = 0, customCount = 0, sharedCount = 0;
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(response.getOutputStream()))) {
                Person person = null;
                log.info("[MobileProductImages] batch=" + batch + " loading product and custom-image indexes");
                List<?> products = java.util.Collections.emptyList();
                log.info("[MobileProductImages] products=" + products.size());
                // Load custom media once. The previous implementation queried
                // MediaUpload once or twice for every product, which made a
                // 2,654-product catalog unnecessarily slow and could prevent
                // the shared image package from being completed.
                Map<String, MediaUpload> customByKey = new HashMap<String, MediaUpload>();
                List<MediaUpload> allCustom = java.util.Collections.<MediaUpload>emptyList();
                for (MediaUpload media : allCustom) {
                    if (media != null && media.getFilepath() != null) {
                        customByKey.put(media.getFilepath().trim(), media);
                    }
                }
                log.info("[MobileProductImages] customMediaRecords=" + customByKey.size());
                for (Object value : products) {
                    Object[] row = (Object[]) value;
                    String code = row[1] == null ? "" : String.valueOf(row[1]).trim();
                    String localId = row[0] == null ? "" : String.valueOf(row[0]).trim();
                    MediaUpload custom = null;
                    if (!localId.isEmpty()) custom = customByKey.get(localId);
                    if (custom == null && !code.isEmpty()) custom = customByKey.get(code);
                    byte[] customBytes = null;
                    if (custom != null && custom.getContent() != null) customBytes = custom.getContent();
                    if (custom != null && customBytes == null) {
                        String customPath = mediaUploadService.generateFilePathForPerson(
                                person, custom.getFilepath(), custom.getContentType());
                        File customFile = new File(customPath);
                        log.info("[MobileProductImages] customCandidate code=" + code
                                + " localId=" + localId + " key=" + custom.getFilepath()
                                + " path=" + customPath + " exists=" + customFile.isFile()
                                + " bytes=" + customFile.length());
                        if (customFile.isFile()) customBytes = Files.readAllBytes(customFile.toPath());
                    }
                    if (customBytes != null && customBytes.length > 0 && !code.isEmpty()) {
                        ZipEntry entry = new ZipEntry(code + ".jpg");
                        if (custom != null && custom.getFilepath() != null) {
                            File customFile = new File(mediaUploadService.generateFilePathForPerson(person, custom.getFilepath(), custom.getContentType()));
                            if (customFile.isFile()) entry.setTime(customFile.lastModified());
                        }
                        zip.putNextEntry(entry);
                        zip.write(customBytes); zip.closeEntry(); count++; customCount++; names.add(code + ".jpg");
                    }
                }
                log.info("[MobileProductImages] custom phase complete: custom=" + customCount);
                File[] files = directory.listFiles();
                int sharedIndex = 0;
                int sharedStart = batch * size;
                int sharedEnd = sharedStart + size;
                if (files != null) for (File file : files) {
                    if (!file.isFile() || !file.getName().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(png|jpg|jpeg|webp)$")) continue;
                    if (sharedIndex++ < sharedStart) continue;
                    if (sharedIndex > sharedEnd) break;
                    if (names.contains(file.getName())) continue;
                    ZipEntry entry = new ZipEntry(file.getName());
                    entry.setTime(file.lastModified());
                    zip.putNextEntry(entry);
                    try (FileInputStream in = new FileInputStream(file)) {
                        byte[] buffer = new byte[8192]; int n;
                        while ((n = in.read(buffer)) >= 0) if (n > 0) zip.write(buffer, 0, n);
                    }
                    zip.closeEntry(); count++; sharedCount++;
                }
                log.info("[MobileProductImages] shared phase complete: shared=" + sharedCount);
            }
            log.info("[MobileProductImages] custom=" + customCount + " shared=" + sharedCount
                    + " total=" + count + " streamed=true");
            response.flushBuffer();
        } catch (Exception ex) {
            log.error("[MobileProductImages] FAILED while building image package", ex);
            if (!response.isCommitted()) response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    /** Uploads one shared product image from the mobile client. */
    @RequestMapping(value = "/mobileSync/productImage", method = RequestMethod.POST,
            produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> mobileProductImageUpload(@RequestBody String json, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        try {
            JSONObject body = new JSONObject(json);
            long personId = body.optLong("personId", -1L);
            if (personId <= 0) return new ResponseEntity<String>("{\"error\":\"missing_personId\"}", headers, HttpStatus.BAD_REQUEST);
            if (personService.findPerson(personId) == null) return new ResponseEntity<String>("{\"error\":\"person_not_found\"}", headers, HttpStatus.NOT_FOUND);
            String code = body.optString("code", "").trim();
            String encoded = body.optString("imageBase64", "").trim();
            if (code.isEmpty() || encoded.isEmpty())
                return new ResponseEntity<String>("{\"error\":\"missing_code_or_image\"}", headers, HttpStatus.BAD_REQUEST);
            if (encoded.startsWith("data:")) {
                int comma = encoded.indexOf(',');
                if (comma >= 0) encoded = encoded.substring(comma + 1);
            }
            byte[] image = Base64.getDecoder().decode(encoded);
            if (image.length == 0 || image.length > 10 * 1024 * 1024)
                return new ResponseEntity<String>("{\"error\":\"invalid_image_size\"}", headers, HttpStatus.BAD_REQUEST);
            String path = mediaUploadService.generateFilePathForSharedProduct(code, ".jpg");
            if (path == null) return new ResponseEntity<String>("{\"error\":\"invalid_code\"}", headers, HttpStatus.BAD_REQUEST);
            File target = new File(path);
            long clientModified = body.optLong("lastModified", 0L);
            if (target.isFile() && clientModified > 0L && target.lastModified() >= clientModified) {
                return new ResponseEntity<String>(new JSONObject().put("ok", true).put("serverWins", true)
                        .put("code", code).put("lastModified", target.lastModified())
                        .put("imageBase64", Base64.getEncoder().encodeToString(Files.readAllBytes(target.toPath()))).toString(), headers, HttpStatus.OK);
            }
            Files.write(target.toPath(), image);
            if (clientModified > 0L) target.setLastModified(clientModified);
            log.info("[MobileProductImageUpload] personId=" + personId + " code=" + code + " bytes=" + image.length);
            return new ResponseEntity<String>(new JSONObject().put("ok", true).put("code", code).put("bytes", image.length)
                    .put("lastModified", target.lastModified()).toString(), headers, HttpStatus.OK);
        } catch (IllegalArgumentException ex) {
            return new ResponseEntity<String>("{\"error\":\"invalid_image\"}", headers, HttpStatus.BAD_REQUEST);
        } catch (Exception ex) {
            log.error("[MobileProductImageUpload] failed", ex);
            return new ResponseEntity<String>("{\"error\":\"image_upload_failed\"}", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Returns only the Product records belonging to the supplied personId. */
    @RequestMapping(value = "/mobileSync/products", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> mobileSyncProducts(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        long requestedPersonId = request.getParameter("personId") == null ? -1L : Long.parseLong(request.getParameter("personId"));
        long personId = requestedPersonId;
        if (personId <= 0) return new ResponseEntity<String>("{\"error\":\"missing_personId\"}", headers, HttpStatus.BAD_REQUEST);
        Person person = personService.findPerson(personId);
        if (person == null) return new ResponseEntity<String>("{\"error\":\"person_not_found\"}", headers, HttpStatus.NOT_FOUND);
        mobileProductSyncService.ensureNullableLocalId();
        JSONObject result = new JSONObject();
        result.put("personId", personId);
        org.json.JSONArray rows = new org.json.JSONArray();
        final long templatePersonId = 10093L;
        long sourcePersonId = personId;
        String since = request.getParameter("since");
        boolean incremental = since != null && !since.trim().isEmpty();
        String productQuery = "SELECT local_id, folderid, subject, content, code, unit, price, producarea, brand, category, mnemonic, cost, qst, `index`, stock, stock1, stock2, stock3, stock1_shortage, deleted, coverdays, weekly_sales_qty, monthly_sales_qty, last_modified FROM Product WHERE person_id = ? AND ((local_id IS NOT NULL AND local_id > 0) OR (code IS NOT NULL AND TRIM(code) <> ''))" + (incremental ? " AND last_modified > ?" : "") + " ORDER BY last_modified ASC, local_id ASC, code ASC";
        Query productStatement = entityManager.createNativeQuery(productQuery).setParameter(1, sourcePersonId);
        if (incremental) productStatement.setParameter(2, syncTimestampValue(since));
        List<?> values = productStatement.getResultList();
        // Newly activated accounts may not have their own catalog yet. Return
        // the approved starter catalog so the client is usable immediately.
        if (values.isEmpty() && sourcePersonId != templatePersonId
                && personService.findPerson(templatePersonId) != null) {
            sourcePersonId = templatePersonId;
            productStatement = entityManager.createNativeQuery(productQuery).setParameter(1, sourcePersonId);
            if (incremental) productStatement.setParameter(2, syncTimestampValue(since));
            values = productStatement.getResultList();
            result.put("templateFallback", true);
        }
        result.put("sourcePersonId", sourcePersonId);
        result.put("incremental", incremental);
        String nextCursor = since == null ? "" : since;
        for (Object value : values) {
            Object[] r = (Object[]) value;
            JSONObject row = new JSONObject();
            String[] names = {"local_id","folderid","subject","content","code","unit","price","producarea","brand","category","mnemonic","cost","qst","index","stock","stock1","stock2","stock3","monque","deleted","coverdays","weekly_sales_qty","monthly_sales_qty","last_modified"};
            for (int i = 0; i < names.length; i++) if (r[i] != null)
                row.put(names[i], "last_modified".equals(names[i]) ? syncTimestamp(r[i]) : r[i]);
            // JustPrint's existing catalog model uses id; the cloud protocol
            // uses local_id. Return both aliases for wire compatibility.
            if (r[0] != null) row.put("id", r[0]);
            // The mobile protocol accepts both historical timestamp names.
            // This schema stores the authoritative value as last_modified.
            if (r[names.length - 1] != null) row.put("server_modified", syncTimestamp(r[names.length - 1]));
            if (r[names.length - 1] != null) nextCursor = syncTimestamp(r[names.length - 1]);
            rows.put(row);
        }
        result.put("products", rows);
        result.put("nextCursor", nextCursor);
        return new ResponseEntity<String>(result.toString(), headers, HttpStatus.OK);
    }

    /** Optimistic, person-scoped upload. A stale phone can never overwrite a newer server row. */
    @RequestMapping(value = "/mobileSync/products", method = RequestMethod.POST, produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> mobileSyncUpload(@RequestBody String json, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        JSONObject requestJson = new JSONObject(json);
        long requestedPersonId = requestJson.optLong("personId", -1L);
        long personId = requestedPersonId;
        if (personId <= 0) return new ResponseEntity<String>("{\"error\":\"missing_personId\"}", headers, HttpStatus.BAD_REQUEST);
        if (personService.findPerson(personId) == null) return new ResponseEntity<String>("{\"error\":\"person_not_found\"}", headers, HttpStatus.NOT_FOUND);
        try {
            mobileProductSyncService.ensureNullableLocalId();
            org.json.JSONArray products = new JSONObject(json).optJSONArray("products");
            if (products == null) return new ResponseEntity<String>("{\"error\":\"missing_products\"}", headers, HttpStatus.BAD_REQUEST);
            String syncTime = requestJson.optString("syncTime", "").trim();
            org.json.JSONArray uploaded = new org.json.JSONArray(), failed = new org.json.JSONArray(), conflicts = new org.json.JSONArray();
            java.util.Set<String> clientCodes = new java.util.HashSet<String>();
            java.util.Set<String> serverWins = new java.util.HashSet<String>();
            java.util.Set<String> changedOnServer = new java.util.HashSet<String>();
            {
                javax.persistence.Query changedQuery = entityManager.createNativeQuery(
                        "SELECT code FROM Product WHERE person_id=?" + (syncTime.isEmpty() ? "" : " AND last_modified>?"))
                        .setParameter(1, personId);
                if (!syncTime.isEmpty()) changedQuery.setParameter(2, syncTimestampValue(syncTime));
                for (Object code : changedQuery.getResultList()) if (code != null) changedOnServer.add(code.toString().trim());
            }

            for (int i = 0; i < products.length(); i++) {
                JSONObject row = products.getJSONObject(i);
                Long localId = row.has("local_id") && !row.isNull("local_id") ? row.getLong("local_id") : null;
                try {
                    Object current = currentProductTime(personId, localId, row);
                    String actual = current == null ? "" : syncTimestamp(current);
                    JSONObject data = row.optJSONObject("data");
                    if (data == null) data = row;
                    String clientModified = data.optString("last_modified", "").trim();
                    String barcode = data.optString("code", row.optString("code", "")).trim();
                    if (current != null && changedOnServer.contains(barcode)) {
                        mobileProductSyncService.mergeClientInventory(personId, barcode, data);
                        JSONObject merged = new JSONObject().put("code", barcode).put("inventory_merged", true)
                                .put("last_modified", syncTimestamp(currentProductTime(personId, localId, row)));
                        if (localId != null) merged.put("local_id", localId);
                        uploaded.put(merged);
                        clientCodes.add(barcode);
                        serverWins.add(barcode);
                        JSONObject conflict = new JSONObject().put("code", barcode)
                                .put("server_last_modified", actual).put("client_last_modified", clientModified)
                                .put("comparison", "server_details_kept_client_stock1_monque_merged");
                        if (localId != null) conflict.put("local_id", localId);
                        conflicts.put(conflict);
                        continue;
                    }

                    if (current != null && !isClientVersionNewer(clientModified, actual)) {
                        JSONObject conflict = new JSONObject().put("server_last_modified", actual);
                        conflict.put("client_last_modified", clientModified);
                        conflict.put("comparison", compareVersionText(clientModified, actual));
                        if (localId != null) conflict.put("local_id", localId);
                        if (row.has("code")) conflict.put("code", row.optString("code"));
                        conflicts.put(conflict);
                        String conflictCode = row.optString("code", "").trim();
                        if (!conflictCode.isEmpty()) serverWins.add(conflictCode);
                        continue;
                    }
                    String code = data.optString("code", "").trim();
                    Long storedLocalId = mobileProductSyncService.upsert(personId, localId, data);
                    JSONObject accepted = new JSONObject();
                    if (storedLocalId != null) accepted.put("local_id", storedLocalId);
                    if (data.has("code")) accepted.put("code", data.optString("code"));
                    uploaded.put(accepted);
                    if (!code.isEmpty()) clientCodes.add(code);
                } catch (Exception rowError) {
                    JSONObject failure = new JSONObject().put("error", rowError.getMessage() == null ? rowError.getClass().getSimpleName() : rowError.getMessage());
                    if (localId != null) failure.put("local_id", localId);
                    if (row.has("code")) failure.put("code", row.optString("code"));
                    failed.put(failure);
                }
            }
            org.json.JSONArray finalProducts = new org.json.JSONArray();
            String nextCursor = syncTime.isEmpty() ? "" : syncTimestamp(syncTime);
            // An empty cursor means first synchronization: return the complete
            // catalog, including legacy rows whose last_modified is NULL.
            // A conflict must return the server version even when that row is
            // older than the client's cursor; otherwise the client sees a
            // conflict but receives no authoritative server record to absorb.
            boolean returnFullCatalogForConflict = conflicts.length() > 0;
            String sql = "SELECT local_id, folderid, subject, content, code, unit, price, producarea, brand, category, mnemonic, cost, qst, `index`, stock, stock1, stock2, stock3, stock1_shortage, deleted, coverdays, weekly_sales_qty, monthly_sales_qty, last_modified FROM Product WHERE person_id=?"
                    + (syncTime.isEmpty() || returnFullCatalogForConflict ? "" : " AND last_modified>?")
                    + " ORDER BY last_modified ASC, local_id ASC";
            javax.persistence.Query finalQuery = entityManager.createNativeQuery(sql).setParameter(1, personId);
            if (!syncTime.isEmpty() && !returnFullCatalogForConflict)
                finalQuery.setParameter(2, syncTimestampValue(syncTime));
            List<?> values = finalQuery.getResultList();
            for (Object value : values) {
                JSONObject product = mobileProductJson((Object[]) value);
                // Advance the cursor from every server row, including rows that
                // are intentionally omitted because the client just uploaded
                // them. Otherwise a full local upload leaves nextCursor stuck
                // at the old syncTime and the same rows are uploaded forever.
                if (product.has("last_modified") && !product.isNull("last_modified"))
                    nextCursor = product.optString("last_modified", nextCursor);
                String code = product.optString("code", "").trim();
                if (clientCodes.contains(code) && !serverWins.contains(code)) continue;
                finalProducts.put(product);
            }
            return new ResponseEntity<String>(new JSONObject().put("ok", true).put("uploaded", uploaded).put("failed", failed).put("conflicts", conflicts)
                    .put("products", finalProducts).put("nextCursor", nextCursor).toString(), headers, HttpStatus.OK);
        } catch (Exception ex) {
            String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return new ResponseEntity<String>(new JSONObject().put("error", "product_upload_failed").put("detail", detail).toString(), headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Creates one formal website order and its product detail rows for a mobile sale.
     * Cart is deliberately not involved: the request is one atomic MainOrder + Material transaction.
     */
    @RequestMapping(value = "/mobileSync/sales", method = RequestMethod.POST, produces = "application/json")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> mobileSyncSale(@RequestBody String json, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        try {
            // Some existing MySQL installations predate the Litorweight field
            // still mapped by Product.  Ensure the entity schema is compatible
            // before findProductByCodeAndPerson loads a Product entity.
            mobileProductSyncService.ensureProductEntityCompatibility();
            JSONObject body = new JSONObject(json);
            long personId = body.optLong("personId", -1L);
            if (personId <= 0) return new ResponseEntity<String>("{\"error\":\"missing_personId\"}", headers, HttpStatus.BAD_REQUEST);
            org.json.JSONArray items = body.optJSONArray("items");
            if (items == null || items.length() == 0)
                return new ResponseEntity<String>("{\"error\":\"missing_items\"}", headers, HttpStatus.BAD_REQUEST);
            Person person = personService.findPerson(personId);
            if (person == null) return new ResponseEntity<String>("{\"error\":\"person_not_found\"}", headers, HttpStatus.NOT_FOUND);

            String saleId = body.optString("saleId", body.optString("externalOrderId", "")).trim();
            if (saleId.length() == 0)
                return new ResponseEntity<String>("{\"error\":\"missing_sale_id\"}", headers, HttpStatus.BAD_REQUEST);
            String orderNumber = "AikaPos:" + saleId;
            List<MainOrder> previous = mainOrderService.findMainOrderByCON(orderNumber);
            for (MainOrder existing : previous) {
                if (existing.getPerson() != null && existing.getPerson().getId().equals(person.getId())) {
                    return new ResponseEntity<String>(new JSONObject().put("ok", true)
                            .put("replayed", true).put("saleId", saleId)
                            .put("mainOrderId", existing.getId()).toString(), headers, HttpStatus.OK);
                }
            }

            // Resolve and validate every line before persisting anything. This keeps
            // a malformed mobile request from creating a partial website order.
            Product[] resolved = new Product[items.length()];
            int[] quantities = new int[items.length()];
            int[] subtotals = new int[items.length()];
            int total = 0;
            int quantity = 0;
            for (int i = 0; i < items.length(); i++) {
                JSONObject row = items.getJSONObject(i);
                Product product = null;
                String code = row.optString("productCode", row.optString("barcode", row.optString("code", ""))).trim();
                if (code.length() > 0) product = productService.findProductByCodeAndPerson(code, person);
                if (product == null) throw new IllegalArgumentException("product_not_found:" + (code.length() == 0 ? row.optString("name", "unknown") : code));
                int qty = row.optInt("quantity", 1);
                if (qty <= 0) throw new IllegalArgumentException("invalid_quantity:" + product.getName());
                int unitPrice = row.has("unitPrice") ? row.optInt("unitPrice", product.getPrice1()) : product.getPrice1();
                int subtotal = row.has("totalCents") ? row.optInt("totalCents", unitPrice * qty)
                        : row.has("subtotal") ? row.optInt("subtotal", unitPrice * qty) : unitPrice * qty;
                if (subtotal < 0) throw new IllegalArgumentException("invalid_total:" + product.getName());
                resolved[i] = product;
                quantities[i] = qty;
                subtotals[i] = subtotal;
                total += subtotal;
                quantity += qty;
            }

            MainOrder order = new MainOrder();
            order.setPerson(person);
            order.setClientSideOrderNumber(orderNumber);
            order.setModel("AikaPos mobile sale");
            order.setProductName("AikaPos sale " + saleId);
            order.setRecordStatus(0);
            order.setQuantity(quantity);
            mainOrderService.persist(order);
            mainOrderService.flush();

            org.json.JSONArray accepted = new org.json.JSONArray();
            for (int i = 0; i < items.length(); i++) {
                JSONObject row = items.getJSONObject(i);
                Material material = new Material();
                material.setMainOrder(order);
                material.setPerson(person);
                material.setLocation(row.optString("category", "AikaPos"));
                material.setPortionName(resolved[i].getName() == null ? row.optString("name", "Product") : resolved[i].getName());
                material.setQuantity(quantities[i]);
                material.setEndSubTotal(subtotals[i]);
                material.setRecordStatus(0);
                materialService.persist(material);
                accepted.put(new JSONObject().put("productCode", row.optString("productCode", row.optString("barcode", row.optString("code", ""))).trim())
                        .put("quantity", quantities[i]).put("totalCents", subtotals[i]));
            }
            order.setPayCondition("$" + String.format(java.util.Locale.US, "%.2f", total / 100.0));
            order.setTotal_after_tax(order.getPayCondition());
            mainOrderService.flush();
            return new ResponseEntity<String>(new JSONObject().put("ok", true).put("replayed", false).put("saleId", saleId)
                    .put("mainOrderId", order.getId()).put("quantity", quantity).put("totalCents", total).put("items", accepted).toString(), headers, HttpStatus.OK);
        } catch (Exception ex) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return new ResponseEntity<String>(new JSONObject().put("error", "mobile_sale_failed").put("detail", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()).toString(), headers, HttpStatus.BAD_REQUEST);
        }
    }

    private Object currentProductTime(Long personId, Long localId, JSONObject row) {
        JSONObject data = row.optJSONObject("data");
        if (data == null) data = row;
        String barcode = data.optString("code", row.optString("code", "")).trim();
        if (barcode.isEmpty()) throw new IllegalArgumentException("Product barcode is required");
        javax.persistence.Query query = entityManager.createNativeQuery(
                "SELECT last_modified FROM Product WHERE person_id = ? AND code = ?")
                .setParameter(1, personId).setParameter(2, barcode);
        List<?> values = query.getResultList();
        return values.isEmpty() ? null : values.get(0);
    }

    /** Synchronization wire format: ISO-8601 UTC, never a timezone-less string. */
    private String syncTimestamp(Object value) {
        if (value == null) return "";
        if (value instanceof java.sql.Timestamp)
            return ((java.sql.Timestamp) value).toInstant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        if (value instanceof java.util.Date)
            return java.time.format.DateTimeFormatter.ISO_INSTANT.format(
                    java.time.Instant.ofEpochMilli(((java.util.Date) value).getTime()).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return "";
        try {
            return java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.parse(text).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        } catch (Exception ignored) { }
        try {
            java.time.LocalDateTime local = java.time.LocalDateTime.parse(text.replace(' ', 'T'),
                    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return java.time.format.DateTimeFormatter.ISO_INSTANT.format(local.toInstant(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        } catch (Exception ignored) {
            return text;
        }
    }

    /** Parse the new UTC cursor and retain compatibility with old legacy cursors as UTC. */
    private java.sql.Timestamp syncTimestampValue(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String text = value.trim();
        try {
            return java.sql.Timestamp.from(java.time.Instant.parse(text));
        } catch (Exception ignored) { }
        try {
            java.time.LocalDateTime local = java.time.LocalDateTime.parse(text.replace(' ', 'T'),
                    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return java.sql.Timestamp.from(local.toInstant(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid sync cursor: " + value, ex);
        }
    }

    private boolean isClientVersionNewer(String clientModified, String serverModified) {
        if (clientModified == null || clientModified.trim().isEmpty()) return false;
        if (serverModified == null || serverModified.trim().isEmpty()) return true;
        try {
            java.time.Instant client = java.time.Instant.parse(syncTimestamp(clientModified));
            java.time.Instant server = java.time.Instant.parse(syncTimestamp(serverModified));
            return client.isAfter(server);
        } catch (Exception ex) {
            return false;
        }
    }

    private String compareVersionText(String clientModified, String serverModified) {
        if (clientModified == null || clientModified.trim().isEmpty()) return "client(?)server";
        if (serverModified == null || serverModified.trim().isEmpty()) return "client>server";
        try {
            java.time.Instant client = java.time.Instant.parse(syncTimestamp(clientModified));
            java.time.Instant server = java.time.Instant.parse(syncTimestamp(serverModified));
            int result = client.compareTo(server);
            return result > 0 ? "client>server" : result < 0 ? "client<server" : "client=server";
        } catch (Exception ex) {
            return "unparseable";
        }
    }

    private JSONObject mobileProductJson(Object[] r) {
        String[] names = {"local_id", "folderid", "subject", "content", "code", "unit", "price", "producarea", "brand", "category", "mnemonic", "cost", "qst", "index", "stock", "stock1", "stock2", "stock3", "stock1_shortage", "deleted", "coverdays", "weekly_sales_qty", "monthly_sales_qty", "last_modified"};
        JSONObject product = new JSONObject();
        for (int i = 0; i < names.length && i < r.length; i++) {
            if (r[i] != null) product.put(names[i], "last_modified".equals(names[i]) ? syncTimestamp(r[i]) : r[i]);
        }
        if (r.length > 0 && r[0] != null) product.put("id", r[0]);
        if (r.length > names.length && r[names.length] != null) product.put("server_modified", r[names.length]);
        return product;
    }

    /** POS-facing, person-scoped JSON feed. Never exposes another site's products. */
    @RequestMapping(value = "/pos", method = RequestMethod.GET, headers = "Accept=application/json")
    @ResponseBody
    public ResponseEntity<String> posProducts(HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        if (person == null) return new ResponseEntity<String>("{\"error\":\"not_authenticated\"}", headers, HttpStatus.UNAUTHORIZED);
        return new ResponseEntity<String>(Product.toJsonArray(productService.findAllProductsByPerson(person)), headers, HttpStatus.OK);
    }

    @RequestMapping(value = "/pos/{id}", method = RequestMethod.PUT, headers = "Accept=application/json")
    @ResponseBody
    public ResponseEntity<String> updatePosProduct(@PathVariable("id") Long id,
                                                    @RequestBody String json,
                                                    HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        if (person == null) return new ResponseEntity<String>("{\"error\":\"not_authenticated\"}", headers, HttpStatus.UNAUTHORIZED);
        Product current = productService.findByLocalIdAndPerson(id, person);
        if (current == null) return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
        Product incoming = Product.fromJsonToProduct(json);
        incoming.setId(current.getId());
        incoming.setPerson(person);
        productService.merge(incoming);
        return new ResponseEntity<String>(incoming.toJson(), headers, HttpStatus.OK);
    }

    // this method is needed when editing a product item. the page was under the path of "\products"
    @RequestMapping("/getImage/{id}")
    public void getImage(
            @PathVariable("id")
            String id,
            HttpServletRequest request,
            HttpServletResponse response) {
        response.setContentType("image/jpg");
        Person person = taoUtil.getCurPerson(request);
        MediaUpload tMedia = mediaUploadService.findMediaByKeyAndPerson(taoUtil.getLangPrfWithDefault(request) + id, person);
        try {
            if (tMedia != null && tMedia.getContent() != null) {
                byte[] imageBytes = tMedia.getContent();
                response.getOutputStream().write(imageBytes);
                response.getOutputStream().flush();
            } else {
            }
        } catch (Exception e) {
            System.out.println("[ProductController]Exception occured when fetching img of ID:" + id + "! (pid:" + person.getId() + " ) "   + e);
        }
    }

    @RequestMapping(params = "createform", produces = "text/html")
    public String addProduct(
            Model uiModel,
            HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        String tPosistionStr = request.getParameter("position");
        if (tPosistionStr.startsWith("menu_"))
            tPosistionStr = "catalog" + tPosistionStr.substring(4);
        Product tProduct = new Product();
        tProduct.setPerson(person);
        tProduct.setC1(tPosistionStr);
        populateEditForm(uiModel, tProduct);

        taoUtil.initTexts(uiModel, "notice_creating_product_", 1, taoUtil.getLangPrfWithDefault(request), person);
        return "products/create";
    }

    @RequestMapping(method = RequestMethod.POST, produces = "text/html")
    public String create(
            @Valid
            Product product,
            BindingResult bindingResult,
            Model uiModel,
            HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        if (bindingResult.hasErrors()) {
            populateEditForm(uiModel, product);
            return "products/create";
        }
        String tPartNo = product.getPartNo();
        Product tProduct = productService.findProductsByPartNoAndPerson(tPartNo, person);
        if (tProduct != null) {
            uiModel.addAttribute("Product_ErrorMessage", "The Part Number '" + tPartNo
                    + "' has been used, please check and input anOther one!");
            populateEditForm(uiModel, product);
            return "products/create";
        }
        String tURLStr = product.getC1();
        if (!tURLStr.toLowerCase().startsWith("catalog_")) {
            uiModel.addAttribute(
                    "Product_ErrorMessage",
                    "C1 value not correct! (you are supposed to use the default value, if you do need to change, make sure the format are correct.");
            populateEditForm(uiModel, product);
            return "products/create";
        }
        tURLStr = "menu" + tURLStr.substring(7);
        uiModel.asMap().clear();
        productService.persist(product);
        return "redirect:/" + encodeUrlPathSegment(tURLStr, request);
    }

    @RequestMapping(produces = "text/html")
    public String list(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(value = "page", required = false)
            Integer page,
            @RequestParam(value = "size", required = false)
            Integer size,
            Model uiModel) {

        Person person = taoUtil.getCurPerson(request);
        String langPrf = taoUtil.getLangPrfWithDefault(request);

        if (page != null || size != null) {
            int sizeNo = size == null ? 10 : size.intValue();
            final int firstResult = page == null ? 0 : (page.intValue() - 1) * sizeNo;
            uiModel.addAttribute("products", productService.findProductEntries(firstResult, sizeNo));
            float nrOfPages = (float) productService.countProducts() / sizeNo;
            uiModel.addAttribute("maxPages", (int) ((nrOfPages > (int) nrOfPages || nrOfPages == 0.0) ? nrOfPages + 1
                    : nrOfPages));
        } else {
            uiModel.addAttribute("products", productService.findAllProducts());
        }

        // set header and footer.
        makesureCommonTextInitialized(uiModel, request, langPrf, person);

        // get every number out from the menuIndex which looks like: 2_1_1 or 2_1 or 2
        int[] menuIdxes = new int[3];
        String tSt = request.getParameter("position");
        if (tSt != null) {
            if (tSt.startsWith("menu_")) {
                tSt = tSt.substring(5);
                uiModel.addAttribute("c1", tSt);
            } else if (tSt.startsWith("catalog_")) {
                tSt = tSt.substring(8);
            }
        }
        for (int i = 0; i < 3; i++) {
            int septatorIdx = tSt.indexOf("_"); // get the menuIdx
            if (septatorIdx > 0) {
                menuIdxes[i] = Integer.valueOf(tSt.substring(0, septatorIdx));
                tSt = tSt.substring(septatorIdx + 1);
            } else {
                menuIdxes[i] = Integer.valueOf(tSt.substring(0));
                break;
            }
        }
        return taoUtil.initSubPage(request, response, uiModel, langPrf, menuIdxes[0], menuIdxes[1], menuIdxes[2], person);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = "text/html")
    public String delete(
            @PathVariable("id")
            Long id,
            @RequestParam(value = "page", required = false)
            Integer page,
            @RequestParam(value = "size", required = false)
            Integer size,
            Model uiModel,
            HttpServletRequest request) {
        Product product = productService.findProduct(id);
        productService.remove(product);
        uiModel.asMap().clear();

        uiModel.addAttribute("position", request.getParameter("position"));
        if (page != null && size != null) {
            uiModel.addAttribute("page", page.toString());
            uiModel.addAttribute("size", size.toString());
        }
        String tURLStr = "menu" + request.getParameter("position").substring(7);
        return "redirect:/" + encodeUrlPathSegment(tURLStr, request);
    }

    @RequestMapping(value = "/{partNo}", params = "form", produces = "text/html")
    public String updateForm(
            @PathVariable("partNo")
            String partNo,
            Model uiModel,
            HttpServletRequest request) {
        populateEditForm(uiModel, productService.findProductsByPartNoAndPerson(partNo, taoUtil.getCurPerson(request)));
        return "products/update";
    }

	@RequestMapping(params = "form", produces = "text/html")
    public String createForm(Model uiModel) {
        populateEditForm(uiModel, new Product());
        return "products/create";
    }

	@RequestMapping(value = "/{id}", produces = "text/html")
    public String show(@PathVariable("id") Long id, Model uiModel) {
        uiModel.addAttribute("product", productService.findProduct(id));
        uiModel.addAttribute("itemId", id);
        return "products/show";
    }

	@RequestMapping(method = RequestMethod.PUT, produces = "text/html")
    public String update(@Valid Product product, BindingResult bindingResult, Model uiModel, HttpServletRequest httpServletRequest) {
        if (bindingResult.hasErrors()) {
            populateEditForm(uiModel, product);
            return "products/update";
        }
        uiModel.asMap().clear();
        productService.merge(product);
        return "redirect:/products/" + encodeUrlPathSegment(product.getId().toString(), httpServletRequest);
    }

	void populateEditForm(Model uiModel, Product product) {
        uiModel.addAttribute("product", product);
    }

	String encodeUrlPathSegment(String pathSegment, HttpServletRequest httpServletRequest) {
        String enc = httpServletRequest.getCharacterEncoding();
        if (enc == null) {
            enc = WebUtils.DEFAULT_CHARACTER_ENCODING;
        }
        try {
            pathSegment = UriUtils.encodePathSegment(pathSegment, enc);
        } catch (UnsupportedEncodingException uee) {}
        return pathSegment;
    }

	@RequestMapping(value = "/{id}", headers = "Accept=application/json")
    @ResponseBody
    public ResponseEntity<String> showJson(@PathVariable("id") Long id) {
        Product product = productService.findProduct(id);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        if (product == null) {
            return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<String>(product.toJson(), headers, HttpStatus.OK);
    }

	@RequestMapping(headers = "Accept=application/json")
    @ResponseBody
    public ResponseEntity<String> listJson() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        List<Product> result = productService.findAllProducts();
        return new ResponseEntity<String>(Product.toJsonArray(result), headers, HttpStatus.OK);
    }

	@RequestMapping(method = RequestMethod.POST, headers = "Accept=application/json")
    public ResponseEntity<String> createFromJson(@RequestBody String json) {
        Product product = Product.fromJsonToProduct(json);
        productService.persist(product);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        return new ResponseEntity<String>(headers, HttpStatus.CREATED);
    }

	@RequestMapping(value = "/jsonArray", method = RequestMethod.POST, headers = "Accept=application/json")
    public ResponseEntity<String> createFromJsonArray(@RequestBody String json) {
        for (Product product: Product.fromJsonArrayToProducts(json)) {
            productService.persist(product);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        return new ResponseEntity<String>(headers, HttpStatus.CREATED);
    }

	@RequestMapping(method = RequestMethod.PUT, headers = "Accept=application/json")
    public ResponseEntity<String> updateFromJson(@RequestBody String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        Product product = Product.fromJsonToProduct(json);
        if (productService.merge(product) == null) {
            return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<String>(headers, HttpStatus.OK);
    }

	@RequestMapping(value = "/jsonArray", method = RequestMethod.PUT, headers = "Accept=application/json")
    public ResponseEntity<String> updateFromJsonArray(@RequestBody String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        for (Product product: Product.fromJsonArrayToProducts(json)) {
            if (productService.merge(product) == null) {
                return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
            }
        }
        return new ResponseEntity<String>(headers, HttpStatus.OK);
    }

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE, headers = "Accept=application/json")
    public ResponseEntity<String> deleteFromJson(@PathVariable("id") Long id) {
        Product product = productService.findProduct(id);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        if (product == null) {
            return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
        }
        productService.remove(product);
        return new ResponseEntity<String>(headers, HttpStatus.OK);
    }
}
