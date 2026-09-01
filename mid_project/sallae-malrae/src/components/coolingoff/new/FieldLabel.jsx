export default function FieldLabel({ children, required = true }) {
  return (
    <div className="mb-2 flex items-center gap-1">
      <label className="text-sm font-semibold text-[#1F2A1F]">{children}</label>
      {required && <span className="text-red-500">*</span>}
    </div>
  );
}
