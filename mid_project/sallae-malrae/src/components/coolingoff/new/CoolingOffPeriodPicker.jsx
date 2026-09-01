"use client";

import { useState } from "react";
import CoolingOffTimePicker from "./CoolingOffTimePicker";

const CALENDAR_WEEK_DAYS = ["일", "월", "화", "수", "목", "금", "토"];
const MONTH_NAMES = [
  "1월","2월","3월","4월","5월","6월",
  "7월","8월","9월","10월","11월","12월",
];

function getCalendarWeeks(year, month) {
  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const cells = [];
  for (let i = 0; i < firstDay; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);
  while (cells.length % 7 !== 0) cells.push(null);
  const weeks = [];
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7));
  return weeks;
}

function formatDateStr(year, month, day) {
  return `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

function getMinHour24() {
  const now = new Date();
  return now.getMinutes() === 0 ? now.getHours() + 1 : now.getHours() + 2;
}

export default function CoolingOffPeriodPicker() {
  const todayDate = new Date();
  todayDate.setHours(0, 0, 0, 0);
  const maxDate = new Date(todayDate);
  maxDate.setDate(maxDate.getDate() + 30);

  const defaultDate = new Date(todayDate);
  defaultDate.setDate(defaultDate.getDate() + 7);

  const [viewYear, setViewYear] = useState(defaultDate.getFullYear());
  const [viewMonth, setViewMonth] = useState(defaultDate.getMonth());
  const [selectedDay, setSelectedDay] = useState(defaultDate.getDate());

  const selectedDateStr = selectedDay
    ? formatDateStr(viewYear, viewMonth, selectedDay)
    : "";
  const calendarWeeks = getCalendarWeeks(viewYear, viewMonth);

  const todayYear = todayDate.getFullYear();
  const todayMonth = todayDate.getMonth();
  const todayMinHour24 = getMinHour24();

  const isDisabled = (day) => {
    if (!day) return true;
    const d = new Date(viewYear, viewMonth, day);
    if (d < todayDate || d > maxDate) return true;
    if (d.getTime() === todayDate.getTime()) return todayMinHour24 > 23;
    return false;
  };

  const isSelectedToday =
    selectedDay !== null &&
    viewYear === todayYear &&
    viewMonth === todayMonth &&
    selectedDay === todayDate.getDate();

  const canGoPrev =
    viewYear > todayYear || (viewYear === todayYear && viewMonth > todayMonth);
  const canGoNext = (() => {
    const nextYear = viewMonth === 11 ? viewYear + 1 : viewYear;
    const nextMonth = viewMonth === 11 ? 0 : viewMonth + 1;
    return new Date(nextYear, nextMonth, 1) <= maxDate;
  })();

  const handlePrev = () => {
    if (viewMonth === 0) {
      setViewMonth(11);
      setViewYear((y) => y - 1);
    } else {
      setViewMonth((m) => m - 1);
    }
    setSelectedDay(null);
  };

  const handleNext = () => {
    if (viewMonth === 11) {
      setViewMonth(0);
      setViewYear((y) => y + 1);
    } else {
      setViewMonth((m) => m + 1);
    }
    setSelectedDay(null);
  };

  return (
    <div className="mt-6">
      <div className="flex flex-wrap items-center gap-2">
        <label htmlFor="decisionDate" className="text-sm font-semibold text-[#1F2A1F]">
          쿨링오프 기간 <span className="text-red-500">*</span>
        </label>
        <span className="rounded-full bg-[#EEF3EC] px-3 py-1 text-xs font-medium text-[#7E8A7C]">
          최대 30일
        </span>
      </div>
      <p className="mt-2 text-sm text-[#9AA49A]">
        며칠 후에도 사고 싶다면 그때 구매해요.
      </p>
      <div className="mx-auto mt-4 max-w-xl rounded-[24px] border border-[#E2E8E0] bg-white p-4 sm:p-5">
        <input
          id="decisionDate"
          name="decisionDate"
          type="hidden"
          value={selectedDateStr}
          readOnly
        />
        <div className="flex items-center justify-between gap-3">
          <button
            type="button"
            onClick={handlePrev}
            disabled={!canGoPrev}
            className="flex h-9 w-9 items-center justify-center rounded-xl border border-[#D6DDD4] text-base font-semibold text-[#5B655D] transition-colors hover:bg-[#F6FAF5] disabled:opacity-30 disabled:cursor-not-allowed"
            aria-label="이전 달 보기"
          >
            &#8249;
          </button>
          <p className="text-base font-semibold text-[#1F2A1F] sm:text-lg">
            {viewYear}년 {MONTH_NAMES[viewMonth]}
          </p>
          <button
            type="button"
            onClick={handleNext}
            disabled={!canGoNext}
            className="flex h-9 w-9 items-center justify-center rounded-xl border border-[#D6DDD4] text-base font-semibold text-[#5B655D] transition-colors hover:bg-[#F6FAF5] disabled:opacity-30 disabled:cursor-not-allowed"
            aria-label="다음 달 보기"
          >
            &#8250;
          </button>
        </div>
        <div className="mt-5 grid grid-cols-7 gap-1.5 text-center text-xs font-medium text-[#A2AAA0]">
          {CALENDAR_WEEK_DAYS.map((day) => (
            <span key={day}>{day}</span>
          ))}
        </div>
        <div className="mt-2.5 grid grid-cols-7 gap-1.5">
          {calendarWeeks.flat().map((day, index) => {
            if (!day) {
              return (
                <div
                  key={`empty-${index}`}
                  aria-hidden="true"
                  className="h-10 rounded-xl border border-transparent"
                />
              );
            }
            const isToday =
              viewYear === todayYear &&
              viewMonth === todayMonth &&
              day === todayDate.getDate();
            const isSelected = day === selectedDay;
            const disabled = isDisabled(day);
            return (
              <button
                key={`${day}-${index}`}
                type="button"
                disabled={disabled}
                aria-pressed={isSelected}
                onClick={() => setSelectedDay(day)}
                className={`relative flex h-10 items-center justify-center rounded-xl text-sm font-medium transition-colors disabled:cursor-default ${
                  isSelected
                    ? "bg-[#E8F1E9] text-[#38503E]"
                    : disabled
                      ? "text-[#C5CCC3]"
                      : "text-[#3B443B] hover:bg-[#F6FAF5]"
                }`}
              >
                {day}
                {isToday && (
                  <span
                    aria-hidden="true"
                    className={`absolute bottom-1 left-1/2 h-1 w-1 -translate-x-1/2 rounded-full ${
                      isSelected ? "bg-[#38503E]" : "bg-[#6D876D]"
                    }`}
                  />
                )}
              </button>
            );
          })}
        </div>
        {selectedDay && (
          <div className="mt-4 rounded-xl bg-[#F8F1E4] px-4 py-3">
            <p className="text-center text-sm text-[#7E6438]">
              {isSelectedToday ? (
                <span className="font-semibold text-[#5C4827]">
                  오늘 안에 다시 생각해볼게요
                </span>
              ) : (
                <>
                  오늘부터{" "}
                  <span className="font-semibold text-[#5C4827]">
                    {Math.round(
                      (new Date(viewYear, viewMonth, selectedDay).getTime() -
                        todayDate.getTime()) /
                        (24 * 60 * 60 * 1000),
                    )}
                    일간
                  </span>{" "}
                  멈춰볼게요
                  <span className="ml-1 text-[#A89878]">
                    ({MONTH_NAMES[todayMonth]} {todayDate.getDate()}일 →{" "}
                    {MONTH_NAMES[viewMonth]} {selectedDay}일)
                  </span>
                </>
              )}
            </p>
          </div>
        )}
        <CoolingOffTimePicker minHour24={isSelectedToday ? todayMinHour24 : null} />
      </div>
    </div>
  );
}
