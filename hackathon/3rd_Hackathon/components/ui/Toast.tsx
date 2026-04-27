'use client';

import { useEffect, useState } from 'react';

interface ToastProps {
  message: string;
  type?: 'error' | 'success' | 'info';
  onClose: () => void;
}

export function Toast({ message, type = 'info', onClose }: ToastProps) {
  useEffect(() => {
    const t = setTimeout(onClose, 3000);
    return () => clearTimeout(t);
  }, [onClose]);

  const bg =
    type === 'error' ? 'bg-red-500' :
    type === 'success' ? 'bg-green-500' : 'bg-gray-700';

  return (
    <div className={`fixed bottom-6 left-1/2 -translate-x-1/2 z-50 px-5 py-3 rounded-xl text-white text-sm font-medium shadow-lg ${bg} animate-fade-in`}>
      {message}
    </div>
  );
}

interface ToastState {
  id: number;
  message: string;
  type: 'error' | 'success' | 'info';
}

let toastQueue: ((toast: ToastState) => void) | null = null;
let counter = 0;

export function showToast(message: string, type: 'error' | 'success' | 'info' = 'info') {
  toastQueue?.({ id: ++counter, message, type });
}

export function ToastContainer() {
  const [toasts, setToasts] = useState<ToastState[]>([]);

  useEffect(() => {
    toastQueue = (toast) => setToasts((prev) => [...prev, toast]);
    return () => { toastQueue = null; };
  }, []);

  return (
    <>
      {toasts.map((t) => (
        <Toast
          key={t.id}
          message={t.message}
          type={t.type}
          onClose={() => setToasts((prev) => prev.filter((x) => x.id !== t.id))}
        />
      ))}
    </>
  );
}
