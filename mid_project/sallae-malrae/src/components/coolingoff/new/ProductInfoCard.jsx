"use client";

import { useState } from "react";
import Card from "@/components/ui/Card";
import Input from "@/components/ui/Input";
import { CATEGORY_OPTIONS } from "@/constants/categories";
import FieldLabel from "./FieldLabel";
import ImageUploadBox from "./ImageUploadBox";

function formatPriceWithCommas(digitsOnly) {
  if (!digitsOnly) return "";
  return Number(digitsOnly).toLocaleString("en-US");
}

export default function ProductInfoCard() {
  const [selectedCategory, setSelectedCategory] = useState("");
  const [priceDisplay, setPriceDisplay] = useState("");

  const handlePriceChange = (event) => {
    const digitsOnly = event.target.value.replace(/\D/g, "");
    setPriceDisplay(formatPriceWithCommas(digitsOnly));
  };

  return (
    <Card className="p-6 sm:p-7">
      <ImageUploadBox />
      <div className="mt-6 grid grid-cols-[minmax(0,1fr)_160px] items-end gap-3 sm:grid-cols-[minmax(0,1fr)_220px] sm:gap-4">
        <div className="min-w-0">
          <Input
            id="name"
            name="name"
            label={<>상품명 <span className="text-red-500">*</span></>}
            placeholder="무엇을 사고 싶나요?"
            maxLength={100}
            className="py-3.5"
          />
        </div>
        <div className="relative min-w-0">
          <Input
            id="price"
            name="price"
            type="text"
            inputMode="numeric"
            value={priceDisplay}
            onChange={handlePriceChange}
            label={<>가격 <span className="text-red-500">*</span></>}
            placeholder="320,000"
            className="py-3.5 pr-10"
          />
          <span className="pointer-events-none absolute bottom-[15px] right-4 text-sm text-gray-400">
            원
          </span>
        </div>
      </div>
      <div className="mt-4">
        <FieldLabel>카테고리</FieldLabel>
        <input
          name="category_id"
          type="hidden"
          value={
            selectedCategory
              ? String(CATEGORY_OPTIONS.indexOf(selectedCategory) + 1)
              : ""
          }
        />
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
          {CATEGORY_OPTIONS.map((category) => {
            const isSelected = category === selectedCategory;
            return (
              <button
                key={category}
                type="button"
                aria-pressed={isSelected}
                onClick={() => setSelectedCategory(category)}
                className={`flex h-[54px] items-center justify-center rounded-xl border px-3 text-sm font-medium transition-colors ${
                  isSelected
                    ? "border-[#8FA58D] bg-[#E8F1E9] text-[#38503E]"
                    : "border-[#D6DDD4] bg-[#FBFCFB] text-[#5B655D] hover:bg-white"
                }`}
              >
                {category}
              </button>
            );
          })}
        </div>
      </div>
    </Card>
  );
}
