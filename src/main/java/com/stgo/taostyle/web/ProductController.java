package com.stgo.taostyle.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

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
import java.util.UUID;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final long MOBILE_TOKEN_MILLIS = 12L * 60L * 60L * 1000L;
    private static final Map<String, MobileSession> MOBILE_SESSIONS = new ConcurrentHashMap<String, MobileSession>();

    private static final class MobileSession {
        final Long personId;
        final long expiresAt;
        MobileSession(Long personId, long expiresAt) { this.personId = personId; this.expiresAt = expiresAt; }
    }
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
            String token = UUID.randomUUID().toString();
            long expires = System.currentTimeMillis() + MOBILE_TOKEN_MILLIS;
            MOBILE_SESSIONS.put(token, new MobileSession(personId, expires));
            return new ResponseEntity<String>(new JSONObject().put("token", token).put("personId", personId).put("expiresAt", expires).toString(), headers, HttpStatus.OK);
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
        // short-lived mobile sync token here; token-gated product and sales
        // APIs remain protected below this endpoint.
        MobileSession session = mobileSession(request);
        log.info("[MobileProductImages] request=" + request.getMethod() + " uri=" + request.getRequestURI()
                + " batch=" + batch + " size=" + size
                + " syncTokenPresent=" + (request.getHeader("X-Mobile-Sync-Token") != null)
                + " authorizationPresent=" + (request.getHeader("Authorization") != null));
        try {
            File directory = new File(File.listRoots()[0], "media" + File.separator + "product");
            log.info("[MobileProductImages] personId=" + (session == null ? "public-shared-images" : session.personId)
                    + " filesystemRoot=" + File.listRoots()[0].getAbsolutePath()
                    + " sharedDir=" + directory.getAbsolutePath() + " exists=" + directory.isDirectory()
                    + " readable=" + directory.canRead());
            if (session != null) {
                File personDirectory = new File(File.listRoots()[0], "media" + File.separator + session.personId);
                log.info("[MobileProductImages] personDir=" + personDirectory.getAbsolutePath()
                        + " exists=" + personDirectory.isDirectory() + " readable=" + personDirectory.canRead());
            }
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
                Person person = session == null ? null : personService.findPerson(session.personId);
                log.info("[MobileProductImages] batch=" + batch + " loading product and custom-image indexes");
                List<?> products = session == null ? java.util.Collections.emptyList()
                        : entityManager.createNativeQuery("SELECT local_id, code FROM Product WHERE person_id = ?")
                        .setParameter(1, session.personId).getResultList();
                log.info("[MobileProductImages] products=" + products.size());
                // Load custom media once. The previous implementation queried
                // MediaUpload once or twice for every product, which made a
                // 2,654-product catalog unnecessarily slow and could prevent
                // the shared image package from being completed.
                Map<String, MediaUpload> customByKey = new HashMap<String, MediaUpload>();
                List<MediaUpload> allCustom = session != null && batch == 0 ? mediaUploadService.findAllMediaUploadByPerson(person)
                        : java.util.Collections.<MediaUpload>emptyList();
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
                        zip.putNextEntry(new ZipEntry(code + ".jpg"));
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
                    zip.putNextEntry(new ZipEntry(file.getName()));
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
        MobileSession session = mobileSession(request);
        if (session == null) return new ResponseEntity<String>("{\"error\":\"sync_not_authorized\"}", headers, HttpStatus.UNAUTHORIZED);
        try {
            JSONObject body = new JSONObject(json);
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
            Files.write(new File(path).toPath(), image);
            log.info("[MobileProductImageUpload] personId=" + session.personId + " code=" + code + " bytes=" + image.length);
            return new ResponseEntity<String>(new JSONObject().put("ok", true).put("code", code).put("bytes", image.length).toString(), headers, HttpStatus.OK);
        } catch (IllegalArgumentException ex) {
            return new ResponseEntity<String>("{\"error\":\"invalid_image\"}", headers, HttpStatus.BAD_REQUEST);
        } catch (Exception ex) {
            log.error("[MobileProductImageUpload] failed", ex);
            return new ResponseEntity<String>("{\"error\":\"image_upload_failed\"}", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Returns only the Product records belonging to the person authorized by the token. */
    @RequestMapping(value = "/mobileSync/products", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> mobileSyncProducts(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        MobileSession session = mobileSession(request);
        if (session == null) return new ResponseEntity<String>("{\"error\":\"sync_not_authorized\"}", headers, HttpStatus.UNAUTHORIZED);
        Person person = personService.findPerson(session.personId);
        if (person == null) return new ResponseEntity<String>("{\"error\":\"person_not_found\"}", headers, HttpStatus.NOT_FOUND);
        mobileProductSyncService.ensureNullableLocalId();
        JSONObject result = new JSONObject();
        result.put("personId", session.personId);
        org.json.JSONArray rows = new org.json.JSONArray();
        final long templatePersonId = 10093L;
        long sourcePersonId = session.personId;
        String productQuery = "SELECT local_id, folderid, subject, content, code, unit, price, producarea, brand, category, mnemonic, cost, qst, `index`, stock, stock1, stock2, stock3, stock1_shortage, deleted, coverdays, weekly_sales_qty, monthly_sales_qty, last_modified FROM Product WHERE person_id = ? AND ((local_id IS NOT NULL AND local_id > 0) OR (code IS NOT NULL AND TRIM(code) <> '')) ORDER BY code";
        List<?> values = entityManager.createNativeQuery(productQuery)
                .setParameter(1, sourcePersonId).getResultList();
        // Newly activated accounts may not have their own catalog yet. Return
        // the approved starter catalog so the client is usable immediately.
        if (values.isEmpty() && sourcePersonId != templatePersonId
                && personService.findPerson(templatePersonId) != null) {
            sourcePersonId = templatePersonId;
            values = entityManager.createNativeQuery(productQuery)
                    .setParameter(1, sourcePersonId).getResultList();
            result.put("templateFallback", true);
        }
        result.put("sourcePersonId", sourcePersonId);
        for (Object value : values) {
            Object[] r = (Object[]) value;
            JSONObject row = new JSONObject();
            String[] names = {"local_id","folderid","subject","content","code","unit","price","producarea","brand","category","mnemonic","cost","qst","index","stock","stock1","stock2","stock3","monque","deleted","coverdays","weekly_sales_qty","monthly_sales_qty","last_modified"};
            for (int i = 0; i < names.length; i++) if (r[i] != null) row.put(names[i], r[i]);
            // JustPrint's existing catalog model uses id; the cloud protocol
            // uses local_id. Return both aliases for wire compatibility.
            if (r[0] != null) row.put("id", r[0]);
            // The mobile protocol accepts both historical timestamp names.
            // This schema stores the authoritative value as last_modified.
            if (r[names.length - 1] != null) row.put("server_modified", r[names.length - 1]);
            rows.put(row);
        }
        result.put("products", rows);
        return new ResponseEntity<String>(result.toString(), headers, HttpStatus.OK);
    }

    /** Optimistic, person-scoped upload. A stale phone can never overwrite a newer server row. */
    @RequestMapping(value = "/mobileSync/products", method = RequestMethod.POST, produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> mobileSyncUpload(@RequestBody String json, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        MobileSession session = mobileSession(request);
        if (session == null) return new ResponseEntity<String>("{\"error\":\"sync_not_authorized\"}", headers, HttpStatus.UNAUTHORIZED);
        try {
            mobileProductSyncService.ensureNullableLocalId();
            org.json.JSONArray products = new JSONObject(json).optJSONArray("products");
            if (products == null) return new ResponseEntity<String>("{\"error\":\"missing_products\"}", headers, HttpStatus.BAD_REQUEST);
            boolean force = new JSONObject(json).optBoolean("force", false);
            org.json.JSONArray uploaded = new org.json.JSONArray(), failed = new org.json.JSONArray(), conflicts = new org.json.JSONArray();
            for (int i = 0; i < products.length(); i++) {
                JSONObject row = products.getJSONObject(i);
                Long localId = row.has("local_id") && !row.isNull("local_id") ? row.getLong("local_id") : null;
                try {
                    String expected = row.optString("expected_last_modified", "");
                    Object current = currentProductTime(session.personId, localId, row);
                    String actual = current == null ? "" : String.valueOf(current);
                    if (!force && !expected.equals(actual)) {
                        JSONObject conflict = new JSONObject().put("server_last_modified", actual);
                        if (localId != null) conflict.put("local_id", localId);
                        if (row.has("code")) conflict.put("code", row.optString("code"));
                        conflicts.put(conflict);
                        continue;
                    }
                    JSONObject data = row.optJSONObject("data");
                    if (data == null) data = row;
                    Long storedLocalId = mobileProductSyncService.upsert(session.personId, localId, data);
                    JSONObject accepted = new JSONObject();
                    if (storedLocalId != null) accepted.put("local_id", storedLocalId);
                    if (data.has("code")) accepted.put("code", data.optString("code"));
                    uploaded.put(accepted);
                } catch (Exception rowError) {
                    JSONObject failure = new JSONObject().put("error", rowError.getMessage() == null ? rowError.getClass().getSimpleName() : rowError.getMessage());
                    if (localId != null) failure.put("local_id", localId);
                    if (row.has("code")) failure.put("code", row.optString("code"));
                    failed.put(failure);
                }
            }
            return new ResponseEntity<String>(new JSONObject().put("ok", true).put("uploaded", uploaded).put("failed", failed).put("conflicts", conflicts).toString(), headers, HttpStatus.OK);
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
        MobileSession mobile = mobileSession(request);
        if (mobile == null) return new ResponseEntity<String>("{\"error\":\"sync_not_authorized\"}", headers, HttpStatus.UNAUTHORIZED);
        try {
            JSONObject body = new JSONObject(json);
            org.json.JSONArray items = body.optJSONArray("items");
            if (items == null || items.length() == 0)
                return new ResponseEntity<String>("{\"error\":\"missing_items\"}", headers, HttpStatus.BAD_REQUEST);
            Person person = personService.findPerson(mobile.personId);
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
                if (row.has("productId") && !row.isNull("productId")) {
                    product = productService.findProduct(row.getLong("productId"));
                    if (product != null && (product.getPerson() == null || !product.getPerson().getId().equals(person.getId()))) product = null;
                }
                String code = row.optString("barcode", row.optString("code", "")).trim();
                if (product == null && code.length() > 0) product = productService.findProductsByPartNoAndPerson(code, person);
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
                accepted.put(new JSONObject().put("productId", resolved[i].getId()).put("quantity", quantities[i]).put("totalCents", subtotals[i]));
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
        String sql = localId != null
                ? "SELECT last_modified FROM Product WHERE person_id = ? AND local_id = ?"
                : "SELECT last_modified FROM Product WHERE person_id = ? AND code = ?";
        javax.persistence.Query query = entityManager.createNativeQuery(sql).setParameter(1, personId);
        if (localId != null) query.setParameter(2, localId);
        else query.setParameter(2, row.optString("code", ""));
        List<?> values = query.getResultList();
        return values.isEmpty() ? null : values.get(0);
    }

    private void upsertProduct(Long personId, long localId, JSONObject d) {
        String sql = "INSERT INTO Product (person_id,local_id,folderid,subject,content,code,unit,price,producarea,brand,category,mnemonic,cost,qst,`index`,stock,stock1,stock2,stock3,stock1_shortage,deleted,coverdays,weekly_sales_qty,monthly_sales_qty,last_modified) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, ?,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE folderid=VALUES(folderid),subject=VALUES(subject),content=VALUES(content),code=VALUES(code),unit=VALUES(unit),price=VALUES(price),producarea=VALUES(producarea),brand=VALUES(brand),category=VALUES(category),mnemonic=VALUES(mnemonic),cost=VALUES(cost),qst=VALUES(qst),`index`=VALUES(`index`),stock=VALUES(stock),stock1=VALUES(stock1),stock2=VALUES(stock2),stock3=VALUES(stock3),stock1_shortage=VALUES(stock1_shortage),deleted=VALUES(deleted),coverdays=VALUES(coverdays),weekly_sales_qty=VALUES(weekly_sales_qty),monthly_sales_qty=VALUES(monthly_sales_qty),last_modified=CURRENT_TIMESTAMP";
        javax.persistence.Query q = entityManager.createNativeQuery(sql);
        Object[] values = {personId, localId, d.optInt("folderid", 0), d.optString("subject", null), d.optString("content", null), d.optString("code", null), d.optString("unit", null), d.optInt("price", 0), d.optString("producarea", null), d.optString("brand", null), d.optString("category", null), d.optString("mnemonic", null), d.optInt("cost", 0), d.optInt("qst", 0), d.optInt("index", 0), d.optInt("stock", 0), d.optInt("stock1", 0), d.optInt("stock2", 0), d.optInt("stock3", 0), d.optInt("monque", 0), d.optInt("deleted", 0), d.optDouble("coverdays", 0), d.optInt("weekly_sales_qty", 0), d.optInt("monthly_sales_qty", 0)};
        for (int i = 0; i < values.length; i++) q.setParameter(i + 1, values[i]);
        q.executeUpdate();
    }

    private MobileSession mobileSession(HttpServletRequest request) {
        String token = request.getHeader("X-Mobile-Sync-Token");
        if (token == null || token.trim().length() == 0) return null;
        MobileSession session = MOBILE_SESSIONS.get(token.trim());
        if (session == null || session.expiresAt < System.currentTimeMillis()) {
            if (session != null) MOBILE_SESSIONS.remove(token.trim());
            return null;
        }
        return session;
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
