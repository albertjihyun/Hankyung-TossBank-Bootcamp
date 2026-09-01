"use client";

import { useState, useEffect, useRef } from "react";
import Image from "next/image";
import FieldLabel from "./FieldLabel";

const ALLOWED_IMAGE_MIME_TYPES = new Set([
  "image/jpeg",
  "image/png",
  "image/webp",
  "image/gif",
]);
const MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;

export default function ImageUploadBox() {
  const fileInputRef = useRef(null);
  const [previewUrl, setPreviewUrl] = useState("");
  const [fileName, setFileName] = useState("");
  const [imageError, setImageError] = useState("");

  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  function handleImageChange(event) {
    const nextFile = event.target.files?.[0];
    if (!nextFile) return;

    if (!ALLOWED_IMAGE_MIME_TYPES.has(nextFile.type)) {
      setImageError("JPG, PNG, WEBP, GIF 형식만 올릴 수 있어요.");
      event.target.value = "";
      return;
    }
    if (nextFile.size > MAX_IMAGE_SIZE_BYTES) {
      setImageError("이미지는 5MB 이하만 올릴 수 있어요.");
      event.target.value = "";
      return;
    }

    setImageError("");
    const nextPreviewUrl = URL.createObjectURL(nextFile);
    setPreviewUrl((previousUrl) => {
      if (previousUrl) URL.revokeObjectURL(previousUrl);
      return nextPreviewUrl;
    });
    setFileName(nextFile.name);
  }

  return (
    <>
      <FieldLabel required={false}>상품 이미지</FieldLabel>
      <div className="rounded-xl border border-[#E2E8E0] bg-[#FBFCFB] px-4 py-3">
        <input
          ref={fileInputRef}
          id="image"
          name="image"
          type="file"
          accept="image/*"
          onChange={handleImageChange}
          className="hidden"
        />
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="inline-flex h-11 items-center justify-center gap-2 self-start rounded-xl border border-[#D6DDD4] bg-white px-4 text-sm font-medium text-[#314231] transition-colors hover:bg-[#F6FAF5]"
          >
            <span className="flex h-6 w-6 items-center justify-center rounded-full bg-[#E8F1E9] text-base text-[#5D7A62]">
              +
            </span>
            사진 업로드
          </button>
          <p className={`text-xs ${imageError ? "text-[#D96C6C] font-medium" : "text-[#8C968A]"}`}>
            {imageError || fileName || "5MB 이하 JPG·PNG·WEBP·GIF 이미지를 올릴 수 있어요."}
          </p>
        </div>
        {previewUrl && (
          <div className="mt-3 overflow-hidden rounded-xl border border-[#D6DDD4] bg-white">
            <Image
              src={previewUrl}
              alt="업로드한 상품 이미지 미리보기"
              width={960}
              height={640}
              unoptimized
              className="h-40 w-full object-cover"
            />
          </div>
        )}{" "}
      </div>
    </>
  );
}
