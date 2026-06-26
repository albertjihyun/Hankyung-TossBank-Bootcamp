package com.hackathon.olive.repository;

import com.hackathon.olive.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByMasterCategory(String masterCategory, Pageable pageable);

    Page<Product> findByArticleType(String articleType, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select distinct p.masterCategory from Product p order by p.masterCategory")
    List<String> findDistinctMasterCategories();
}
