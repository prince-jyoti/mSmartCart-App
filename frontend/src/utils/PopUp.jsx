const PopUp = ({
  open,
  onClose,
  children,
  title,
  width = "max-w-md",
  height = "",
}) => {
  // width: Tailwind max-w-* class, height: Tailwind h-* or min-h-* class
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-40">
      <div
        className={`bg-white rounded-2xl shadow-2xl w-full ${width} ${height} p-6 relative animate-fade-in`}
        style={{ maxHeight: "90vh", overflowY: "auto" }}
      >
        {/* Close (X) button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-gray-400 hover:text-red-500 text-2xl font-bold focus:outline-none"
          aria-label="Close popup"
        >
          &times;
        </button>
        {/* Optional title */}
        {title && (
          <h3 className="text-xl font-bold text-gray-800 mb-4 text-center">
            {title}
          </h3>
        )}
        {/* Popup content */}
        <div className="mb-6">{children}</div>
      </div>
    </div>
  );
};

export default PopUp;
