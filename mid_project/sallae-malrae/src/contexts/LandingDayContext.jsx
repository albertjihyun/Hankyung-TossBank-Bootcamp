"use client";

import { createContext, useContext, useState } from "react";

const LandingDayContext = createContext(null);

export function LandingDayProvider({ children }) {
  const [day, setDay] = useState(3);
  return (
    <LandingDayContext.Provider value={{ day, setDay }}>
      {children}
    </LandingDayContext.Provider>
  );
}

export function useLandingDay() {
  return useContext(LandingDayContext);
}
