import { useEffect, useState } from "react";
import { ORDER_STATUS } from "../utils/orderStatus";

export function StatusBadge({ status }) {
  const meta = ORDER_STATUS[status] || { label: status, badge: "bg-gray-100 text-gray-700" };
  return <span className={`px-3 py-1 rounded-full text-xs font-semibold ${meta.badge}`}>{meta.label}</span>;
}

// Counts down from the server's expiresInSeconds (taken at `fetchedAt`), so the browser's clock
// and timezone don't matter.
export function ExpiryCountdown({ expiresInSeconds, fetchedAt }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);
  if (expiresInSeconds == null || !fetchedAt) return null;
  const left = Math.max(0, expiresInSeconds - Math.floor((now - fetchedAt) / 1000));
  if (left === 0) return <span>The payment window has closed.</span>;
  const m = Math.floor(left / 60);
  const s = String(left % 60).padStart(2, "0");
  return (
    <span>
      Pay within <strong>{m}:{s}</strong> — after that the order expires and its items go back on sale.
    </span>
  );
}
