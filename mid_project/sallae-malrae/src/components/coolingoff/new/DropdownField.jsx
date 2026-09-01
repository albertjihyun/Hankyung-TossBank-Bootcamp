"use client";

import { useState, useEffect, useRef } from "react";

function SelectChevron({ className = "", isOpen = false }) {
  return (
    <span
      aria-hidden="true"
      className={`pointer-events-none absolute inset-y-0 flex items-center text-[#7E8A7C] ${className}`}
    >
      <span className="flex h-6 w-6 items-center justify-center rounded-full border border-[#E2E8E0] bg-white shadow-sm">
        <svg
          viewBox="0 0 20 20"
          fill="none"
          className={`h-3.5 w-3.5 transition-transform ${isOpen ? "rotate-180" : ""}`}
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            d="M5.5 7.75 10 12.25l4.5-4.5"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </span>
    </span>
  );
}

export default function DropdownField({
  id,
  name,
  options,
  placeholder = "선택",
  defaultValue = "",
  value: controlledValue,
  onChange: onChangeProp,
  disabledValues = [],
  suffix = "",
  className = "",
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [internalValue, setInternalValue] = useState(defaultValue);
  const isControlled = controlledValue !== undefined;
  const selectedValue = isControlled ? controlledValue : internalValue;
  const dropdownRef = useRef(null);

  useEffect(() => {
    function handlePointerDown(event) {
      if (!dropdownRef.current?.contains(event.target)) setIsOpen(false);
    }
    function handleKeyDown(event) {
      if (event.key === "Escape") setIsOpen(false);
    }
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

  const displayLabel = selectedValue ? `${selectedValue}${suffix}` : placeholder;

  return (
    <div ref={dropdownRef} className={`relative ${className}`}>
      <input name={name} type="hidden" value={selectedValue} />
      <button
        id={id}
        type="button"
        aria-expanded={isOpen}
        aria-haspopup="listbox"
        onClick={() => setIsOpen((open) => !open)}
        className={`flex h-12 w-full items-center rounded-xl border border-[#D6DDD4] bg-white px-4 text-left text-sm font-medium outline-none transition-[border-color,box-shadow,background-color] focus:border-[#8EAA92] focus:ring-4 focus:ring-[#E8F1E9] ${
          suffix ? "pr-18" : "pr-14"
        } ${selectedValue ? "text-[#1F2A1F]" : "text-[#9AA49A]"}`}
      >
        <span className="truncate">{displayLabel}</span>
      </button>
      {suffix && selectedValue && (
        <span className="pointer-events-none absolute inset-y-0 right-12 flex items-center text-sm font-medium text-[#7E8A7C]">
          {suffix}
        </span>
      )}
      <SelectChevron className="right-3" isOpen={isOpen} />
      {isOpen && (
        <div className="absolute inset-x-0 top-full z-30 mt-2 overflow-hidden rounded-2xl border border-[#D6DDD4] bg-white shadow-[0_18px_40px_-22px_rgba(49,66,49,0.45)]">
          <ul role="listbox" aria-labelledby={id} className="max-h-56 overflow-y-auto p-2">
            {options.map((option) => {
              const isSelected = option === selectedValue;
              return (
                <li key={option}>
                  <button
                    type="button"
                    role="option"
                    aria-selected={isSelected}
                    onClick={() => {
                      if (disabledValues.includes(option)) return;
                      if (isControlled) {
                        onChangeProp?.(option);
                      } else {
                        setInternalValue(option);
                      }
                      setIsOpen(false);
                    }}
                    className={`flex w-full items-center justify-between rounded-xl px-3 py-2.5 text-sm font-medium transition-colors ${
                      disabledValues.includes(option)
                        ? "text-[#C5CCC3] cursor-not-allowed"
                        : isSelected
                          ? "bg-[#E8F1E9] text-[#38503E]"
                          : "text-[#314231] hover:bg-[#F6FAF5]"
                    }`}
                  >
                    <span>{suffix ? `${option}${suffix}` : option}</span>
                    {isSelected && (
                      <svg
                        viewBox="0 0 16 16"
                        fill="none"
                        className="h-4 w-4 text-[#5D7A62]"
                        xmlns="http://www.w3.org/2000/svg"
                      >
                        <path
                          d="m3.5 8 2.5 2.5L12.5 4"
                          stroke="currentColor"
                          strokeWidth="1.8"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        />
                      </svg>
                    )}
                  </button>
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </div>
  );
}
