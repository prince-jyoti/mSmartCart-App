import { useEffect } from "react";
import { useDispatch } from "react-redux";
import { Link, useLocation, useParams } from "react-router-dom";
import useOrderStatus from "./hooks/useOrderStatus";
import useCheckout from "./hooks/useCheckout";
import { fetchCart } from "../slices/cartSlice";
import { ExpiryCountdown, StatusBadge } from "./OrderWidgets";
import { canPay, formatDateTime, formatRupees } from "../utils/orderStatus";

const HEADLINES = {
  PAID: { icon: "✅", title: "Payment received", text: "Your order is confirmed.", tone: "from-green-50 to-emerald-100" },
  PENDING: { icon: "🕒", title: "Awaiting payment", text: "This order hasn't been paid yet.", tone: "from-amber-50 to-orange-100" },
  PAYMENT_FAILED: { icon: "⚠️", title: "Payment failed", text: "The payment didn't go through. You can try again.", tone: "from-red-50 to-rose-100" },
  EXPIRED: { icon: "⌛", title: "Order expired", text: "It wasn't paid in time, so its items went back on sale.", tone: "from-gray-50 to-slate-200" },
};

export default function OrderStatus() {
  const { orderId } = useParams();
  const location = useLocation();
  const dispatch = useDispatch();
  const justPaid = Boolean(location.state?.justPaid);
  const { order, error, polling, timedOut, fetchedAt, reload } = useOrderStatus(orderId, { waitForPayment: justPaid });
  const { payForOrder } = useCheckout();

  // Bought items leave the cart on the server once the order is paid; refresh the navbar count.
  useEffect(() => {
    if (order?.status === "PAID") dispatch(fetchCart());
  }, [order?.status, dispatch]);

  if (error && !order) {
    return (
      <div className="max-w-md mx-auto text-center py-16">
        <p className="text-red-600 mb-4">{error}</p>
        <Link to="/orders" className="text-indigo-600 font-medium">Back to my orders</Link>
      </div>
    );
  }
  if (!order) {
    return <div className="text-center py-16 text-gray-500">Loading order…</div>;
  }

  const confirming = justPaid && polling && order.status === "PENDING";
  const head = confirming
    ? { icon: "⏳", title: "Confirming your payment…", text: "This usually takes a few seconds.", tone: "from-indigo-50 to-purple-100" }
    : HEADLINES[order.status] || HEADLINES.PENDING;

  return (
    <div className={`min-h-[80vh] bg-gradient-to-br ${head.tone} flex items-center justify-center rounded-3xl`}>
      <div className="max-w-md w-full mx-auto px-6 py-10">
        <div className="text-center mb-8">
          <div className={`mx-auto w-20 h-20 bg-white rounded-full flex items-center justify-center mb-4 shadow ${confirming ? "animate-pulse" : ""}`}>
            <span className="text-4xl">{head.icon}</span>
          </div>
          <h1 className="text-3xl font-bold text-gray-800 mb-2">{head.title}</h1>
          <p className="text-gray-600">{head.text}</p>
          {timedOut && order.status === "PENDING" && (
            <p className="mt-3 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg p-3">
              {location.state?.confirmingLater
                ? "We couldn't reach Razorpay to confirm your payment yet. We'll confirm it automatically within a few minutes; you don't need to pay again."
                : "We haven't received confirmation yet. If you completed the payment it will show up here shortly."}{" "}
              <button onClick={reload} className="underline font-medium">Check again</button>
            </p>
          )}
          {location.state?.verifyFailed && order.status !== "PAID" && (
            <p className="mt-3 text-sm text-red-700">
              We couldn't verify that payment. If money was deducted, contact support with the order ID below.
            </p>
          )}
        </div>

        <div className="bg-white rounded-2xl shadow-lg p-6 mb-6">
          <div className="flex justify-between items-center mb-1">
            <span className="font-mono text-xs text-gray-500 break-all">{order.orderId}</span>
            <StatusBadge status={order.status} />
          </div>
          <p className="text-xs text-gray-500 mb-4">Placed {formatDateTime(order.createdAt)}</p>
          <div className="divide-y divide-gray-100">
            {order.items?.map((item) => (
              <div key={item.id} className="flex items-center gap-3 py-3">
                {item.image && <img src={item.image} alt={item.title} className="w-12 h-12 object-cover rounded-lg border" />}
                <div className="flex-1">
                  <p className="font-medium text-gray-800">{item.title}</p>
                  <p className="text-xs text-gray-500">Qty {item.quantity} × {formatRupees(item.price)}</p>
                </div>
                <span className="font-semibold text-gray-700">{formatRupees(item.price * item.quantity)}</span>
              </div>
            ))}
          </div>
          <div className="flex justify-between items-center pt-4 mt-2 border-t">
            <span className="text-gray-600">Total</span>
            <span className="text-xl font-bold text-gray-900">{formatRupees(order.totalAmount)}</span>
          </div>
        </div>

        {canPay(order) && !confirming && !location.state?.confirmingLater && (
          <div className="mb-6 text-center">
            <p className="text-sm text-gray-600 mb-3">
              <ExpiryCountdown expiresInSeconds={order.expiresInSeconds} fetchedAt={fetchedAt} />
            </p>
            <button
              onClick={() => payForOrder(order)}
              className="w-full bg-gradient-to-r from-indigo-600 to-purple-600 text-white py-3 rounded-lg font-semibold hover:from-indigo-700 hover:to-purple-700 shadow-lg"
            >
              {order.status === "PAYMENT_FAILED" ? "Try payment again" : "Pay now"}
            </button>
          </div>
        )}

        <div className="flex gap-3">
          <Link to="/" className="flex-1 text-center border border-gray-300 bg-white text-gray-700 py-3 rounded-lg font-semibold hover:bg-gray-50">
            Continue shopping
          </Link>
          <Link to="/orders" className="flex-1 text-center border border-gray-300 bg-white text-gray-700 py-3 rounded-lg font-semibold hover:bg-gray-50">
            My orders
          </Link>
        </div>
      </div>
    </div>
  );
}
