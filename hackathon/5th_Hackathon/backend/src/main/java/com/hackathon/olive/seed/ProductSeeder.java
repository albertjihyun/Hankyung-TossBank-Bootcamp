package com.hackathon.olive.seed;

import com.hackathon.olive.domain.Product;
import com.hackathon.olive.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 기획서 3-[3] 단계: seed-products.csv(실제 Kaggle 메타데이터)를 읽어 H2에 적재.
 * 가격/할인/재고는 데이터셋에 없으므로 카테고리 규칙 기반 고정 난수로 생성(재현 가능).
 * 이미지는 CSV의 image_url(=/products/{id}.jpg, Next.js 정적 서빙).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Value("${seed.enabled:false}")
    private boolean seedEnabled;

    @Value("${seed.csv:seed-products.csv}")
    private String seedCsvPath;

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            log.info("[seed] disabled — skip");
            return;
        }
        if (productRepository.count() > 0) {
            log.info("[seed] products already present ({}) — skip", productRepository.count());
            return;
        }

        Path csv = Path.of(seedCsvPath);
        if (!Files.isRegularFile(csv)) {
            log.warn("[seed] seed csv not found at '{}' (abs: {}). " +
                    "scripts/prepare-seed.py 를 먼저 실행해 Kaggle 데이터를 준비하세요. — skip",
                    seedCsvPath, csv.toAbsolutePath());
            return;
        }

        List<Product> batch = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) { // 0 = header
                String line = lines.get(i);
                if (line.isBlank()) continue;
                String[] c = line.split(",", -1);
                if (c.length < 9) continue;

                long id = Long.parseLong(c[0].trim());
                Product p = new Product();
                p.setId(id);
                p.setName(blankToNull(c[1]));
                p.setGender(blankToNull(c[2]));
                p.setMasterCategory(blankToNull(c[3]));
                p.setSubCategory(blankToNull(c[4]));
                p.setArticleType(blankToNull(c[5]));
                p.setBaseColour(blankToNull(c[6]));
                p.setSeason(blankToNull(c[7]));
                p.setImageUrl(c[8].trim());

                // 가격/할인/재고: id 기반 고정 난수
                Random r = new Random(id);
                int[] range = priceRange(p.getMasterCategory(), p.getSubCategory(), p.getArticleType());
                int steps = (range[1] - range[0]) / 1000;
                int price = range[0] + (steps > 0 ? r.nextInt(steps + 1) : 0) * 1000 + 900;
                p.setPrice(price);
                p.setDiscountRate(r.nextInt(10) < 7 ? 0 : 5 + r.nextInt(10) * 5);
                p.setStock(r.nextInt(10) == 0 ? 0 : r.nextInt(201));

                batch.add(p);
            }
        } catch (Exception e) {
            log.error("[seed] failed reading csv", e);
            return;
        }

        productRepository.saveAll(batch);
        log.info("[seed] inserted {} products from {}", batch.size(), seedCsvPath);
    }

    private static String blankToNull(String s) {
        String t = s == null ? null : s.trim();
        return (t == null || t.isEmpty()) ? null : t;
    }

    /** master/sub/article 기반 가격 구간(원). 기획서 3-[3] 규칙 근사. */
    private static int[] priceRange(String master, String sub, String article) {
        String a = (article == null ? "" : article).toLowerCase();
        String s = (sub == null ? "" : sub).toLowerCase();
        if ("Footwear".equals(master)) return new int[]{39900, 129900};
        if ("Accessories".equals(master)) return new int[]{9900, 49900};
        // Apparel: 아우터류는 고가
        boolean outer = s.contains("outer") || a.contains("jacket") || a.contains("coat")
                || a.contains("sweater") || a.contains("blazer");
        return outer ? new int[]{49900, 159900} : new int[]{19900, 59900};
    }
}
