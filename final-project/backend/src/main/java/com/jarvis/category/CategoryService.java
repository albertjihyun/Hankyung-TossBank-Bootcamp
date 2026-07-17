package com.jarvis.category;

import com.jarvis.category.dto.CategoryTreeResponse;
import com.jarvis.global.response.BusinessException;
import com.jarvis.global.response.ErrorCode;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryTreeResponse> getTree() {
        List<Category> all = categoryRepository.findAllByOrderByIdAsc();
        Map<Long, List<Category>> childrenByParent = all.stream()
                .filter(c -> !c.isRoot())
                .collect(Collectors.groupingBy(Category::getParentId));
        return all.stream()
                .filter(Category::isRoot)
                .map(root -> CategoryTreeResponse.from(root,
                        childrenByParent.getOrDefault(root.getId(), List.of())))
                .toList();
    }

    public Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** P-6 브랜드홈 필터용 소분류 요약 — id 순 정렬 (02 D20) */
    public List<CategoryTreeResponse.Child> getSummaries(Collection<Long> ids) {
        return categoryRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(Category::getId))
                .map(CategoryTreeResponse.Child::from)
                .toList();
    }
}
