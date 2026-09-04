package com.lubrimanager.category;

import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.category.dto.CreateCategoryRequest;
import com.lubrimanager.category.dto.UpdateCategoryRequest;
import com.lubrimanager.product.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<Category> list(boolean onlyActive) {
        return onlyActive ? categories.findByActiveTrueOrderByNameAsc() : categories.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Category get(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("Catégorie %d introuvable".formatted(id)));
    }

    public Category create(CreateCategoryRequest req) {
        String name = req.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("Une catégorie porte déjà ce nom.");
        }
        Category category = new Category();
        category.setName(name);
        category.setDescription(clean(req.description()));
        category.setActive(true);
        return categories.save(category);
    }

    public Category update(Long id, UpdateCategoryRequest req) {
        Category category = get(id);
        String name = req.name().trim();
        if (!name.equalsIgnoreCase(category.getName()) && categories.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("Une catégorie porte déjà ce nom.");
        }
        category.setName(name);
        category.setDescription(clean(req.description()));
        category.setActive(req.active());
        return category;
    }

    public void delete(Long id) {
        Category category = get(id);
        if (products.existsByCategoryId(id)) {
            throw ApiException.businessRule(
                    "Cette catégorie est utilisée par des produits : désactivez-la plutôt que de la supprimer.");
        }
        categories.delete(category);
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
