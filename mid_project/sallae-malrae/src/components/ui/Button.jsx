const VARIANT_STYLES = {
  primary:
    "bg-[#8FA58D] text-white hover:bg-[#81967F] disabled:bg-[#B8C5B6] disabled:text-white/70",
  secondary:
    "border border-[#9AB79E] bg-white text-[#5D7A62] hover:bg-[#E8F1E9] disabled:border-[#C8D5C6] disabled:text-[#9CAB99]",
  ghost:
    "bg-transparent text-[#5D7A62] hover:bg-[#E8F1E9] disabled:text-[#9CAB99]",
  neutral:
    "bg-gray-200 text-gray-700 hover:bg-gray-300 disabled:bg-gray-100 disabled:text-gray-400",
};

const SIZE_STYLES = {
  sm: "h-10 rounded-2xl px-4 text-sm",
  md: "h-12 rounded-[20px] px-6 text-sm",
  lg: "h-14 rounded-[22px] px-6 text-base",
};

export default function Button({
  type = "button",
  children,
  onClick,
  disabled = false,
  variant = "primary",
  size = "md",
  fullWidth = false,
  className = "",
  ...props
}) {
  const variantClassName = VARIANT_STYLES[variant] ?? VARIANT_STYLES.primary;
  const sizeClassName = SIZE_STYLES[size] ?? SIZE_STYLES.md;

  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={`
        inline-flex cursor-pointer items-center justify-center font-medium transition-colors
        disabled:cursor-not-allowed
        ${fullWidth ? "w-full" : ""}
        ${sizeClassName}
        ${variantClassName}
        ${className}
      `}
      {...props}
    >
      {children}
    </button>
  );
}
