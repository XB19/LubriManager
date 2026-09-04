package com.lubrimanager.product;

import com.lubrimanager.category.Category;
import com.lubrimanager.category.CategoryRepository;
import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.common.reference.ReferenceCounterService;
import com.lubrimanager.product.dto.CreateProductRequest;
import com.lubrimanager.product.dto.UpdateProductRequest;
import java.math.BigDecimal;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Catalogue produits : attribution de la référence, gestion des prix (TTC),
 * du taux de TVA et du seuil d'alerte de stock. Le stock lui-même est géré
 * en Phase 7.
 */
@Service
@Transactional
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final String PREFIX = "PROD";
    private static final int WIDTH = 6;
    private static final BigDecimal DEFAULT_VAT_RATE = new BigDecimal("18.00");

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ReferenceCounterService references;

    public ProductService(ProductRepository products, CategoryRepository categories,
                          ReferenceCounterService references) {
        this.products = products;
        this.categories = categories;
        this.references = references;
    }

    @Transactional(readOnly = true)
    public Page<Product> search(String q, Long categoryId, ProductStatus status, boolean lowStockOnly,
                                Pageable pageable) {
        String pattern = StringUtils.hasText(q)
                ? "%" + q.trim().toLowerCase(Locale.ROOT) + "%"
                : "%";
        return products.search(pattern, categoryId, status, lowStockOnly, pageable);
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        return products.findById(id)
                .orElseThrow(() -> ApiException.notFound("Produit %d introuvable".formatted(id)));
    }

    @Transactional(readOnly = true)
    public Product getByReference(String reference) {
        return products.findByReference(reference)
                .orElseThrow(() -> ApiException.notFound("Produit %s introuvable".formatted(reference)));
    }

    public Product create(CreateProductRequest req) {
        Category category = resolveCategory(req.categoryId());
        Product product = new Product();
        product.setReference(references.next(PREFIX, WIDTH));
        product.setCategory(category);
        product.setStatus(ProductStatus.ACTIF);
        product.setStockQuantity(BigDecimal.ZERO);
        apply(product, req.name(), req.brand(), req.description(), req.unit(),
                req.unitPrice(), req.vatRate() != null ? req.vatRate() : DEFAULT_VAT_RATE,
                req.alertThreshold() != null ? req.alertThreshold() : BigDecimal.ZERO);
        Product saved = products.save(product);
        log.info("Produit créé : {} — {} ({} FCFA TTC)", saved.getReference(), saved.getName(), saved.getUnitPrice());
        return saved;
    }

    public Product update(Long id, UpdateProductRequest req) {
        Product product = get(id);
        Category category = resolveCategory(req.categoryId());
        product.setCategory(category);
        apply(product, req.name(), req.brand(), req.description(), req.unit(),
                req.unitPrice(), req.vatRate(), req.alertThreshold());
        return product;
    }

    public Product updateStatus(Long id, ProductStatus status) {
        Product product = get(id);
        product.setStatus(status);
        return product;
    }

    // ------------------------------------------------------------------

    private void apply(Product p, String name, String brand, String description, String unit,
                       BigDecimal unitPrice, BigDecimal vatRate, BigDecimal alertThreshold) {
        if (unitPrice.stripTrailingZeros().scale() > 0) {
            throw ApiException.businessRule("Le prix doit être un nombre entier de FCFA.");
        }
        p.setName(name.trim());
        p.setBrand(StringUtils.hasText(brand) ? brand.trim() : null);
        p.setDescription(StringUtils.hasText(description) ? description.trim() : null);
        p.setUnit(unit.trim());
        p.setUnitPrice(unitPrice.setScale(2));
        p.setVatRate(vatRate.setScale(2));
        p.setAlertThreshold(alertThreshold.setScale(2));
    }

    private Category resolveCategory(Long categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("Catégorie %d introuvable".formatted(categoryId)));
    }
}
