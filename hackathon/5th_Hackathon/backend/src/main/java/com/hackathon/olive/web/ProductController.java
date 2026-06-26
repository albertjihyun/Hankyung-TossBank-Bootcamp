package com.hackathon.olive.web;

import com.hackathon.olive.domain.Product;
import com.hackathon.olive.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;

    /** 상품 목록: 페이지네이션 + 카테고리(master_category) 필터 */
    @GetMapping("/products")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size,
            @RequestParam(required = false) String category) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<Product> result = (category != null && !category.isBlank())
                ? productRepository.findByMasterCategory(category, pageable)
                : productRepository.findAll(pageable);

        return Map.of(
                "content", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "first", result.isFirst(),
                "last", result.isLast()
        );
    }

    /** 카테고리 목록 (필터 탭용) */
    @GetMapping("/categories")
    public List<String> categories() {
        return productRepository.findDistinctMasterCategories();
    }

    /** 상품 단건 상세 */
    @GetMapping("/products/{id}")
    public ResponseEntity<Product> detail(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
