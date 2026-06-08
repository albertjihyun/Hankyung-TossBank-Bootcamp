package com.hackathon.openrun.domain;

/**
 * 행사 카테고리 (TECH_SPEC §2: POPUP/CLASS/SHOW/MARKET/ETC).
 */
public enum Category {
    POPUP("팝업"),
    CLASS("클래스"),
    SHOW("공연"),
    MARKET("마켓"),
    ETC("기타");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
