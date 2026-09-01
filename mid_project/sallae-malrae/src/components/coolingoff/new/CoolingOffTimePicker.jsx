"use client";

import { useState } from "react";
import DropdownField from "./DropdownField";

const TIME_PERIOD_OPTIONS = ["오전", "오후"];
const AM_HOURS = Array.from({ length: 12 }, (_, i) => String(i).padStart(2, "0"));
const PM_HOURS = Array.from({ length: 12 }, (_, i) => String(i + 12).padStart(2, "0"));

function getMinValidTime(minHour24) {
  for (const h of AM_HOURS) {
    if (parseInt(h, 10) >= minHour24) return { period: "오전", hour: h };
  }
  for (const h of PM_HOURS) {
    if (parseInt(h, 10) >= minHour24) return { period: "오후", hour: h };
  }
  return null;
}

export default function CoolingOffTimePicker({ minHour24 }) {
  const [period, setPeriod] = useState("오후");
  const [hour, setHour] = useState("20");

  const effectiveTime =
    minHour24 !== null && parseInt(hour, 10) < minHour24
      ? (getMinValidTime(minHour24) ?? { period, hour })
      : { period, hour };

  const disabledPeriods =
    minHour24 !== null
      ? TIME_PERIOD_OPTIONS.filter((p) => {
          const hours = p === "오전" ? AM_HOURS : PM_HOURS;
          return hours.every((h) => parseInt(h, 10) < minHour24);
        })
      : [];

  const currentHours = effectiveTime.period === "오전" ? AM_HOURS : PM_HOURS;
  const disabledHours =
    minHour24 !== null
      ? currentHours.filter((h) => parseInt(h, 10) < minHour24)
      : [];

  const handlePeriodChange = (newPeriod) => {
    setPeriod(newPeriod);
    setHour(newPeriod === "오전" ? "00" : "12");
  };

  return (
    <div className="mt-4 rounded-[20px] bg-[#FBFCFB] p-4">
      <div className="flex flex-wrap items-center gap-2">
        <label htmlFor="decisionPeriod" className="text-sm font-semibold text-[#1F2A1F]">
          다시 볼 시간 <span className="text-red-500">*</span>
        </label>
        <span className="text-xs text-[#9AA49A]">
          차분히 다시 결정할 수 있는 시간으로 골라보세요.
        </span>
      </div>
      <div className="mt-3 grid gap-2.5 sm:mx-auto sm:max-w-[340px] sm:grid-cols-[104px_minmax(0,1fr)]">
        <DropdownField
          id="decisionPeriod"
          name="decisionPeriod"
          options={TIME_PERIOD_OPTIONS}
          value={effectiveTime.period}
          onChange={handlePeriodChange}
          disabledValues={disabledPeriods}
        />
        <DropdownField
          id="decisionHour"
          name="decisionHour"
          options={currentHours}
          value={effectiveTime.hour}
          onChange={setHour}
          suffix="시"
          disabledValues={disabledHours}
        />
      </div>
      <p className="mt-3 text-xs text-[#9AA49A]">
        예: 오후 8시에 다시 생각할 수 있게 설정해요.
      </p>
    </div>
  );
}
