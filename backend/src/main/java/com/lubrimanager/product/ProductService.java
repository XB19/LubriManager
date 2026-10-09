package com.lubrimanager.product;

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
 * Catalogue produits : attribution de la référence et gestion des champs
 * (nom, marque, unité, prix, description). Modèle minimal, sans TVA ni stock.
 */
@Service
@Transactional
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final String PREFIX = "PROD";
    private static final int WIDTH = 6;

    private final ProductRepository products;
    private final ReferenceCounterService references;

    public ProductService(ProductRepository products, ReferenceCounterService references) {
        this.products = products;
        this.references = references;
    }

    @Transactional(readOnly = true)
    public Page<Product> search(String q, Pageable pageable) {
        String pattern = StringUtils.hasText(q)
                ? "%" + q.trim().toLowerCase(Locale.ROOT) + "%"
                : "%";
        return products.search(pattern, pageable);
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
        Product product = new Product();
        product.setReference(references.next(PREFIX, WIDTH));
        apply(product, req.name(), req.brand(), req.unit(), req.unitPrice(), req.description());
        Product saved = products.save(product);
        log.info("Produit créé : {} — {} ({} FCFA)", saved.getReference(), saved.getName(), saved.getUnitPrice());
        return saved;
    }

    public Product update(Long id, UpdateProductRequest req) {
        Product product = get(id);
        apply(product, req.name(), req.brand(), req.unit(), req.unitPrice(), req.description());
        return product;
    }

    // ------------------------------------------------------------------

    private void apply(Product p, String name, String brand, String unit, BigDecimal unitPrice, String description) {
        if (unitPrice.stripTrailingZeros().scale() > 0) {
            throw ApiException.businessRule("Le prix doit être un nombre entier de FCFA.");
        }
        p.setName(name.trim());
        p.setBrand(brand.trim());
        p.setUnit(unit.trim());
        p.setUnitPrice(unitPrice.setScale(2));
        p.setDescription(StringUtils.hasText(description) ? description.trim() : null);
    }
}
