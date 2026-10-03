package com.stgo.taostyle.service;

import com.stgo.taostyle.web.CC;
import com.stgo.taostyle.domain.Customize;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.MediaUpload;
import com.stgo.taostyle.domain.Service;
import com.stgo.taostyle.domain.TextContent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@org.springframework.stereotype.Service
public class PosProductBootstrapService {

    private static final int FIRST_SERVICE_MENU = 11;
    private static final int LAST_SERVICE_MENU = 20;
    private static final int DEFAULT_CATEGORY_COL = 5;
    private static final int DEFAULT_CATEGORY_ROW = 4;
    private static final int MAX_SUB_MENU_SLOTS_TO_CLEAR = 50;
    private static final Map<String, String> DEPANNEUR_THEME = createDepanneurTheme();

    @PersistenceContext
    private EntityManager entityManager;

    private final ServiceService serviceService;
    private final TextContentService textContentService;
    private final CustomizeService customizeService;
    private final MediaUploadService mediaUploadService;

    public PosProductBootstrapService(ServiceService serviceService,
                                      TextContentService textContentService,
                                      CustomizeService customizeService,
                                      MediaUploadService mediaUploadService) {
        this.serviceService = serviceService;
        this.textContentService = textContentService;
        this.customizeService = customizeService;
        this.mediaUploadService = mediaUploadService;
    }

    @Transactional
    public int importProductsForPerson(Person person) {
        if (person == null || person.getId() == null) {
            return 0;
        }

        serviceService.ensureExtendedSchema();
        clearImportedPosServices(person);

        List<ProductRow> products = loadProducts(person);
        if (isDepanneurSite(person)) {
            applyDepanneurTheme(person);
            applyDepanneurLuckyWheelDefaults(person, products);
        }
        if (products.isEmpty()) {
            return 0;
        }

        LayoutDefinition layout = buildLayout(person);
        clearServiceMenuSlots(person);
        ensureMenuStructure(person, layout);

        Map<String, Integer> pageCounters = new LinkedHashMap<>();
        int imported = 0;
        for (ProductRow row : products) {
            MenuTarget target = layout.resolve(row.category);
            if (target == null) {
                target = layout.defaultTarget();
            }
            if (target == null) {
                continue;
            }

            int index = pageCounters.getOrDefault(target.pageKey, 0) + 1;
            pageCounters.put(target.pageKey, index);

            String serviceKey = target.pageKey + "_0_" + index;
            Service service = serviceService.findServiceByCatalogAndPerson(serviceKey, person);
            if (service == null) {
                service = new Service();
                service.setPerson(person);
                service.setC1(serviceKey);
            }

            service.setName(resolveDisplayName(row, index));
            service.setDescription(null);
            service.setPrice(row.price);
            service.setCost(row.cost);
            service.setStock(row.stock);
            service.setWeeklySalesQty(row.weeklySalesQty);
            service.setMonthlySalesQty(row.monthlySalesQty);
            service.setC2(row.code);
            service.setRecordStatus(index);

            if (service.getId() == null) {
                serviceService.persist(service);
            } else {
                serviceService.merge(service);
            }

            upsertDescriptionText(person, "en_service_" + serviceKey + "_description", row.content, service);
            upsertDescriptionText(person, "fr_service_" + serviceKey + "_description", row.content, service);
            upsertDescriptionText(person, "zh_service_" + serviceKey + "_description", row.content, service);
            imported++;
        }

        return imported;
    }

    private boolean isDepanneurSite(Person person) {
        Customize customize = customizeService.findCustomizeByKeyAndPerson(CC.is_depanneur_site, person);
        if (customize == null || StringUtils.isBlank(customize.getCusValue())) {
            return false;
        }
        String value = customize.getCusValue();
        return CC.TRUE.equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value);
    }

    private void applyDepanneurTheme(Person person) {
        for (Map.Entry<String, String> entry : DEPANNEUR_THEME.entrySet()) {
            upsertCustomizeIfMissing(person, entry.getKey(), entry.getValue());
        }
        upsertCustomizeIfMissing(person, CC.show_lucky_wheel, CC.TRUE);
    }

    private void applyDepanneurLuckyWheelDefaults(Person person, List<ProductRow> products) {
        Customize customize = customizeService.findCustomizeByKeyAndPerson(CC.lucky_wheel_prizes, person);
        if (customize != null && StringUtils.isNotBlank(customize.getCusValue())) {
            return;
        }

        String defaultConfig = buildDefaultLuckyPrizeConfig(products);
        if (StringUtils.isNotBlank(defaultConfig)) {
            upsertCustomize(person, CC.lucky_wheel_prizes, defaultConfig);
        }
    }

    private void upsertCustomizeIfMissing(Person person, String key, String value) {
        Customize customize = customizeService.findCustomizeByKeyAndPerson(key, person);
        if (customize != null && StringUtils.isNotBlank(customize.getCusValue())) {
            return;
        }
        upsertCustomize(person, key, value);
    }

    private static Map<String, String> createDepanneurTheme() {
        Map<String, String> theme = new LinkedHashMap<>();
        theme.put("background_app", "f3f8fb");
        theme.put("foreground_app", "314754");
        theme.put("background_menu", "a9cfdf");
        theme.put("foreground_menu", "2f4b59");
        theme.put("background_leftbar", "b5d8e6");
        theme.put("foreground_leftbar", "365261");
        theme.put("background_subpage", "fbfeff");
        theme.put("background_operator", "a9bff0");
        theme.put("foreground_operator", "ffffff");
        theme.put("hover_background_operator", "8fb0e4");
        theme.put("color_operator", "ffffff");
        theme.put("link_mouseover", "b86c93");
        theme.put("background_footArea", "9fc3d7");
        theme.put("foreground_footArea", "42606e");
        theme.put("background_foot", "eef6fa");
        theme.put("moneyColor", "73ab72");
        return theme;
    }

    private String buildDefaultLuckyPrizeConfig(List<ProductRow> products) {
        if (products == null || products.isEmpty()) {
            return "";
        }

        List<ProductRow> picks = new ArrayList<>();
        addLuckyPrizePick(picks, products, "naya", "water", "eau");
        addLuckyPrizePick(picks, products, "bic", "lighter", "briquet");
        addLuckyPrizePick(picks, products, "excel", "gum", "gomme");
        addLuckyPrizePick(picks, products, "chip", "chips", "lays", "ruffles", "doritos");
        addLuckyPrizePick(picks, products, "mr noodles", "mr noodle", "noodle", "ramen");

        if (picks.isEmpty()) {
            for (ProductRow product : products) {
                if (product == null || StringUtils.isBlank(product.code) || containsPrizeCode(picks, product.code)) {
                    continue;
                }
                picks.add(product);
                if (picks.size() >= 5) {
                    break;
                }
            }
        }

        List<String> parts = new ArrayList<>();
        int[] weights = new int[]{15, 15, 20, 25, 25};
        for (int i = 0; i < picks.size(); i++) {
            ProductRow product = picks.get(i);
            if (product == null || StringUtils.isBlank(product.code)) {
                continue;
            }
            parts.add(product.code.trim() + ":" + weights[Math.min(i, weights.length - 1)]);
        }
        return StringUtils.join(parts, ",");
    }

    private void addLuckyPrizePick(List<ProductRow> picks, List<ProductRow> products, String... keywords) {
        for (ProductRow product : products) {
            if (product == null || StringUtils.isBlank(product.code) || containsPrizeCode(picks, product.code)) {
                continue;
            }
            String haystack = (StringUtils.defaultString(product.mnemonic) + " "
                    + StringUtils.defaultString(product.subject) + " "
                    + StringUtils.defaultString(product.content)).toLowerCase(Locale.ENGLISH);
            for (String keyword : keywords) {
                if (haystack.contains(keyword.toLowerCase(Locale.ENGLISH))) {
                    picks.add(product);
                    return;
                }
            }
        }
    }

    private boolean containsPrizeCode(List<ProductRow> picks, String code) {
        for (ProductRow product : picks) {
            if (product != null && StringUtils.equalsIgnoreCase(StringUtils.trimToEmpty(product.code), StringUtils.trimToEmpty(code))) {
                return true;
            }
        }
        return false;
    }

    private List<ProductRow> loadProducts(Person person) {
        String orderedQuery = "SELECT mnemonic, subject, content, code, price, cost, stock, weekly_sales_qty, monthly_sales_qty, category, `index` " +
                "FROM Product " +
                "WHERE person_id = ? AND (deleted IS NULL OR deleted <> -1) " +
                "ORDER BY CASE WHEN `index` IS NULL THEN 1 ELSE 0 END, `index`, " +
                "LOWER(COALESCE(NULLIF(TRIM(mnemonic), ''), NULLIF(TRIM(subject), ''), code, '')), local_id";
        String fallbackQuery = "SELECT mnemonic, subject, content, code, price, cost, stock, weekly_sales_qty, monthly_sales_qty, category " +
                "FROM Product " +
                "WHERE person_id = ? AND (deleted IS NULL OR deleted <> -1) " +
                "ORDER BY LOWER(COALESCE(NULLIF(TRIM(mnemonic), ''), NULLIF(TRIM(subject), ''), code, '')), local_id";

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(hasProductIndexColumn() ? orderedQuery : fallbackQuery)
                .setParameter(1, person.getId().intValue())
                .getResultList();
        return mapProductRows(rows);
    }

    private boolean hasProductIndexColumn() {
        try {
            Object count = entityManager.createNativeQuery(
                            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Product' AND COLUMN_NAME = 'index'")
                    .getSingleResult();
            return toInteger(count) > 0;
        } catch (Exception ignore) {
            return false;
        }
    }

    private List<ProductRow> mapProductRows(List<Object[]> rows) {
        List<ProductRow> products = new ArrayList<>();
        for (Object[] row : rows) {
            ProductRow product = new ProductRow();
            product.mnemonic = cleanString(row[0]);
            product.subject = cleanString(row[1]);
            product.content = cleanString(row[2]);
            product.code = cleanString(row[3]);
            product.price = toInteger(row[4]);
            product.cost = toInteger(row[5]);
            product.stock = toInteger(row[6]);
            product.weeklySalesQty = toInteger(row[7]);
            product.monthlySalesQty = toInteger(row[8]);
            product.category = cleanString(row[9]);
            products.add(product);
        }
        return products;
    }

    private LayoutDefinition buildLayout(Person person) {
        int categoryCol = readRemoteConfigInt(person, "Category_Col", DEFAULT_CATEGORY_COL);
        if (categoryCol <= 0) {
            categoryCol = DEFAULT_CATEGORY_COL;
        }
        int categoryRow = readRemoteConfigInt(person, "Category_Row", DEFAULT_CATEGORY_ROW);
        if (categoryRow <= 0) {
            categoryRow = DEFAULT_CATEGORY_ROW;
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                        "SELECT lang1, dsp_index, local_id " +
                                "FROM Category " +
                                "WHERE person_id = ? AND lang1 IS NOT NULL AND TRIM(lang1) <> '' " +
                                "AND dsp_index IS NOT NULL AND dsp_index >= 0 " +
                                "ORDER BY dsp_index, local_id")
                .setParameter(1, person.getId().intValue())
                .getResultList();

        List<String> orderedCategories = new ArrayList<>();
        for (Object[] row : rows) {
            String name = cleanString(row[0]);
            if (StringUtils.isBlank(name)) {
                continue;
            }
            orderedCategories.add(name);
        }

        if (orderedCategories.isEmpty()) {
            return LayoutDefinition.defaultLayout();
        }

        LayoutDefinition layout = new LayoutDefinition();
        int menuNumber = FIRST_SERVICE_MENU;
        int pageSize = Math.max(1, categoryRow * categoryCol);
        MenuTarget otherTopMenu = null;

        for (int pageStart = 0; pageStart < orderedCategories.size(); pageStart += pageSize) {
            int pageEnd = Math.min(pageStart + pageSize, orderedCategories.size());
            for (int rowStart = pageStart; rowStart < pageEnd; rowStart += categoryCol) {
                int rowEnd = Math.min(rowStart + categoryCol, pageEnd);
                List<String> rowCategories = orderedCategories.subList(rowStart, rowEnd);
                if (rowCategories.isEmpty()) {
                    continue;
                }

                if (otherTopMenu != null) {
                    appendChildren(layout, otherTopMenu, rowCategories);
                    continue;
                }

                if (menuNumber > LAST_SERVICE_MENU) {
                    break;
                }

                String topLevel = rowCategories.get(0);
                if (StringUtils.isBlank(topLevel)) {
                    continue;
                }

                int otherIndex = indexOfOther(rowCategories);
                if (otherIndex >= 0) {
                    MenuTarget otherTarget = MenuTarget.topLevel(menuNumber, rowCategories.get(otherIndex));
                    layout.topMenus.add(otherTarget);
                    layout.targetsByCategory.put(normalize(otherTarget.displayName), otherTarget);
                    appendChildren(layout, otherTarget, rowCategories.subList(0, otherIndex));
                    appendChildren(layout, otherTarget, rowCategories.subList(otherIndex + 1, rowCategories.size()));
                    otherTopMenu = otherTarget;
                    menuNumber++;
                    continue;
                }

                MenuTarget parentTarget = MenuTarget.topLevel(menuNumber, topLevel);
                layout.topMenus.add(parentTarget);
                layout.targetsByCategory.put(normalize(topLevel), parentTarget);
                appendChildren(layout, parentTarget, rowCategories.subList(1, rowCategories.size()));
                menuNumber++;
            }
        }

        return layout.topMenus.isEmpty() ? LayoutDefinition.defaultLayout() : layout;
    }

    private void appendChildren(LayoutDefinition layout, MenuTarget parentTarget, List<String> childCategories) {
        int subIndex = parentTarget.children.size() + 1;
        for (String childCategory : childCategories) {
            if (StringUtils.isBlank(childCategory)) {
                continue;
            }
            MenuTarget childTarget = MenuTarget.subMenu(parentTarget.menuNumber, subIndex, childCategory);
            parentTarget.children.add(childTarget);
            layout.targetsByCategory.put(normalize(childCategory), childTarget);
            subIndex++;
        }
    }

    private boolean isOtherCategory(String categoryName) {
        return "other".equals(normalize(categoryName));
    }

    private int indexOfOther(List<String> categories) {
        for (int i = 0; i < categories.size(); i++) {
            if (isOtherCategory(categories.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private void ensureMenuStructure(Person person, LayoutDefinition layout) {
        upsertCustomize(person, "support_subMenu", "true");

        for (MenuTarget topMenu : layout.topMenus) {
            upsertCustomize(person, "CONTENTTYPE_" + topMenu.menuNumber, "SERVICE");
            upsertMenuTexts(person, "menu_" + topMenu.menuNumber, topMenu.displayName);
            upsertServiceTitle(person, String.valueOf(topMenu.menuNumber), topMenu.displayName);

            for (MenuTarget child : topMenu.children) {
                upsertCustomize(person, "CONTENTTYPE_" + topMenu.menuNumber + "_" + child.subMenuNumber, "SERVICE");
                upsertMenuTexts(person, "menu_" + topMenu.menuNumber + "_" + child.subMenuNumber, child.displayName);
                upsertServiceTitle(person, child.pageKey, child.displayName);
            }
        }
    }

    private void clearServiceMenuSlots(Person person) {
        for (int menuNumber = FIRST_SERVICE_MENU; menuNumber <= LAST_SERVICE_MENU; menuNumber++) {
            removeCustomize(person, "CONTENTTYPE_" + menuNumber);
            removeMenuTexts(person, "menu_" + menuNumber);
            removeServiceTitle(person, String.valueOf(menuNumber));

            for (int subMenuNumber = 1; subMenuNumber <= MAX_SUB_MENU_SLOTS_TO_CLEAR; subMenuNumber++) {
                String pageKey = menuNumber + "_" + subMenuNumber;
                removeCustomize(person, "CONTENTTYPE_" + pageKey);
                removeMenuTexts(person, "menu_" + pageKey);
                removeServiceTitle(person, pageKey);
            }
        }
    }

    private void clearImportedPosServices(Person person) {
        List<Service> services = serviceService.findServiceByPerson(person);
        if (services == null || services.isEmpty()) {
            return;
        }
        for (Service service : services) {
            if (!isImportedPosService(service)) {
                continue;
            }
            removeServiceDescriptionTexts(person, service.getC1());
            serviceService.deleteService(service);
        }
    }

    private void clearImportedPosMediaUploads(Person person) {
        List<MediaUpload> mediaUploads = mediaUploadService.findAllMediaUploadByPerson(person);
        if (mediaUploads == null || mediaUploads.isEmpty()) {
            return;
        }
        for (MediaUpload mediaUpload : mediaUploads) {
            if (mediaUpload == null || !isImportedPosServiceMedia(mediaUpload.getFilepath())) {
                continue;
            }
            mediaUploadService.remove(mediaUpload);
        }
    }

    private boolean isImportedPosServiceMedia(String filepath) {
        if (StringUtils.isBlank(filepath)) {
            return false;
        }
        String key = filepath.trim();
        if (key.startsWith("en_service_") || key.startsWith("fr_service_") || key.startsWith("zh_service_")) {
            key = key.substring(3);
        }
        if (!key.startsWith("service_")) {
            return false;
        }
        if (key.endsWith("_thum")) {
            key = key.substring(0, key.length() - 5);
        }
        String[] parts = key.substring(8).split("_");
        if (parts.length != 3) {
            return false;
        }
        try {
            int topMenu = Integer.parseInt(parts[0]);
            Integer.parseInt(parts[1]);
            Integer.parseInt(parts[2]);
            return topMenu >= FIRST_SERVICE_MENU && topMenu <= LAST_SERVICE_MENU;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
    private boolean isImportedPosService(Service service) {
        if (service == null || StringUtils.isBlank(service.getC1())) {
            return false;
        }
        String[] parts = service.getC1().split("_");
        if (parts.length != 4 && parts.length != 5) {
            return false;
        }
        if (!"0".equals(parts[parts.length - 2])) {
            return false;
        }
        try {
            int topMenu = Integer.parseInt(parts[0]);
            if (topMenu < FIRST_SERVICE_MENU || topMenu > LAST_SERVICE_MENU) {
                return false;
            }
            for (String part : parts) {
                Integer.parseInt(part);
            }
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private void removeServiceDescriptionTexts(Person person, String serviceKey) {
        removeSimpleText(person, "en_service_" + serviceKey + "_description");
        removeSimpleText(person, "fr_service_" + serviceKey + "_description");
        removeSimpleText(person, "zh_service_" + serviceKey + "_description");
    }

    private void upsertMenuTexts(Person person, String menuKey, String content) {
        upsertSimpleText(person, "en_" + menuKey, content);
        upsertSimpleText(person, "fr_" + menuKey, content);
        upsertSimpleText(person, "zh_" + menuKey, content);
    }

    private void removeMenuTexts(Person person, String menuKey) {
        removeSimpleText(person, "en_" + menuKey);
        removeSimpleText(person, "fr_" + menuKey);
        removeSimpleText(person, "zh_" + menuKey);
    }

    private void upsertServiceTitle(Person person, String pageKey, String content) {
        upsertSimpleText(person, "en_service_" + pageKey + "_title", content);
        upsertSimpleText(person, "fr_service_" + pageKey + "_title", content);
        upsertSimpleText(person, "zh_service_" + pageKey + "_title", content);
    }

    private void removeServiceTitle(Person person, String pageKey) {
        removeSimpleText(person, "en_service_" + pageKey + "_title");
        removeSimpleText(person, "fr_service_" + pageKey + "_title");
        removeSimpleText(person, "zh_service_" + pageKey + "_title");
    }

    private void upsertDescriptionText(Person person, String key, String content, Service service) {
        List<String> lines = new ArrayList<>();
        if (isMeaningfulContent(content)) {
            lines.add(content.trim());
        }
        if (lines.isEmpty() && StringUtils.isNotBlank(service.getName())) {
            lines.add(service.getName());
        }
        upsertSimpleText(person, key, String.join("<br/>", lines));
    }

    private boolean isMeaningfulContent(String content) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        String normalized = content.trim();
        return !"false".equalsIgnoreCase(normalized) && !"true".equalsIgnoreCase(normalized);
    }

    private String resolveDisplayName(ProductRow row, int index) {
        if (StringUtils.isNotBlank(row.mnemonic)) {
            return row.mnemonic;
        }
        if (StringUtils.isNotBlank(row.subject)) {
            return row.subject;
        }
        return "Product " + index;
    }

    private void upsertSimpleText(Person person, String key, String content) {
        TextContent textContent = textContentService.findContentsByKeyAndPerson(key, person);
        if (textContent == null) {
            textContent = new TextContent();
            textContent.setPerson(person);
            textContent.setPosInPage(key);
        }
        textContent.setContent(content);
        textContent.setMarkDate(new Date());
        if (textContent.getId() == null) {
            textContentService.persist(textContent);
        } else {
            textContentService.merge(textContent);
        }
    }

    private void removeSimpleText(Person person, String key) {
        TextContent textContent = textContentService.findContentsByKeyAndPerson(key, person);
        if (textContent != null) {
            textContentService.remove(textContent);
        }
    }

    private void upsertCustomize(Person person, String key, String value) {
        Customize customize = customizeService.findCustomizeByKeyAndPerson(key, person);
        if (customize == null) {
            customize = new Customize();
            customize.setPerson(person);
            customize.setCusKey(key);
        }
        customize.setCusValue(value);
        if (customize.getId() == null) {
            customizeService.persist(customize);
        } else {
            customizeService.merge(customize);
        }
    }

    private void removeCustomize(Person person, String key) {
        Customize customize = customizeService.findCustomizeByKeyAndPerson(key, person);
        if (customize != null) {
            customizeService.remove(customize);
        }
    }

    private int readRemoteConfigInt(Person person, String key, int defaultValue) {
        try {
            List<?> results = entityManager.createNativeQuery(
                            "SELECT cfg_value FROM app_user_config WHERE license = ? AND cfg_key = ? ORDER BY updated_at DESC LIMIT 1")
                    .setParameter(1, String.valueOf(person.getId()))
                    .setParameter(2, key)
                    .getResultList();
            if (results == null || results.isEmpty() || results.get(0) == null) {
                return defaultValue;
            }
            return Integer.parseInt(String.valueOf(results.get(0)).trim());
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private String cleanString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.length() == 0) {
            return null;
        }
        return Integer.valueOf(text);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static class ProductRow {
        private String mnemonic;
        private String subject;
        private String content;
        private String code;
        private Integer price;
        private Integer cost;
        private Integer stock;
        private Integer weeklySalesQty;
        private Integer monthlySalesQty;
        private String category;
    }

    private static class LayoutDefinition {
        private final List<MenuTarget> topMenus = new ArrayList<>();
        private final Map<String, MenuTarget> targetsByCategory = new LinkedHashMap<>();

        private MenuTarget resolve(String category) {
            MenuTarget direct = targetsByCategory.get(normalizeStatic(category));
            if (direct != null) {
                return direct;
            }
            return defaultTarget();
        }

        private MenuTarget defaultTarget() {
            return topMenus.isEmpty() ? null : topMenus.get(0);
        }

        private static LayoutDefinition defaultLayout() {
            LayoutDefinition layout = new LayoutDefinition();
            MenuTarget target = MenuTarget.topLevel(FIRST_SERVICE_MENU, "Products");
            layout.topMenus.add(target);
            layout.targetsByCategory.put("", target);
            return layout;
        }

        private static String normalizeStatic(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static class MenuTarget {
        private final int menuNumber;
        private final int subMenuNumber;
        private final String displayName;
        private final String pageKey;
        private final List<MenuTarget> children = new ArrayList<>();

        private MenuTarget(int menuNumber, int subMenuNumber, String displayName, String pageKey) {
            this.menuNumber = menuNumber;
            this.subMenuNumber = subMenuNumber;
            this.displayName = displayName;
            this.pageKey = pageKey;
        }

        private static MenuTarget topLevel(int menuNumber, String displayName) {
            return new MenuTarget(menuNumber, 0, displayName, String.valueOf(menuNumber));
        }

        private static MenuTarget subMenu(int menuNumber, int subMenuNumber, String displayName) {
            return new MenuTarget(menuNumber, subMenuNumber, displayName, menuNumber + "_" + subMenuNumber);
        }
    }
}


