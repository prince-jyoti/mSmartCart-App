// How each order status is shown to the customer.
export const ORDER_STATUS = {
  PENDING: { label: "Awaiting payment", badge: "bg-amber-100 text-amber-800" },
  PAYMENT_FAILED: { label: "Payment failed", badge: "bg-red-100 text-red-700" },
  PAID: { label: "Paid", badge: "bg-green-100 text-green-800" },
  EXPIRED: { label: "Expired", badge: "bg-gray-200 text-gray-700" },
};

export const canPay = (order) => Boolean(order) && (order.status === "PENDING" || order.status === "PAYMENT_FAILED");

export const formatRupees = (amount) =>
  `₹${Number(amount || 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

// Order and payment times come from the server as UTC instants ("...Z"); show them in the viewer's time zone.
export const formatDateTime = (iso) =>
  iso ? new Date(iso).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }) : "";
