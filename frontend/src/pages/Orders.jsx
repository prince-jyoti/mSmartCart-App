import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { Link } from "react-router-dom";
import { fetchMyOrders } from "../slices/orderSlice";
import useCheckout from "./hooks/useCheckout";
import { StatusBadge } from "./OrderWidgets";
import { canPay, formatDateTime, formatRupees } from "../utils/orderStatus";

export default function Orders() {
  const dispatch = useDispatch();
  const { list, listStatus, listError } = useSelector((state) => state.order);
  const { payForOrder } = useCheckout();

  useEffect(() => {
    dispatch(fetchMyOrders());
  }, [dispatch]);

  // Newest first.
  const orders = [...list].sort((a, b) => b.id - a.id);

  return (
    <div className="max-w-3xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-800">My orders</h1>
        <button onClick={() => dispatch(fetchMyOrders())} className="text-sm text-indigo-600 font-medium">Refresh</button>
      </div>

      {listStatus === "loading" && orders.length === 0 && <p className="text-gray-500">Loading…</p>}
      {listError && <p className="text-red-600">{listError}</p>}
      {listStatus === "succeeded" && orders.length === 0 && (
        <p className="text-gray-500">No orders yet. <Link to="/" className="text-indigo-600">Start shopping</Link></p>
      )}

      <div className="space-y-4">
        {orders.map((order) => (
          <div key={order.id} className="bg-white rounded-2xl shadow p-5 border border-gray-100">
            <div className="flex items-center justify-between gap-3 mb-2">
              <Link to={`/orders/${order.orderId}`} className="font-mono text-xs text-gray-500 hover:text-indigo-600 break-all">
                {order.orderId}
              </Link>
              <StatusBadge status={order.status} />
            </div>
            <p className="text-xs text-gray-500 mb-1">{formatDateTime(order.createdAt)}</p>
            <p className="text-sm text-gray-600 mb-3">
              {order.items?.map((i) => `${i.title} × ${i.quantity}`).join(", ")}
            </p>
            <div className="flex items-center justify-between">
              <span className="font-bold text-gray-900">{formatRupees(order.totalAmount)}</span>
              <div className="flex gap-2">
                {canPay(order) && (
                  <button
                    onClick={() => payForOrder(order)}
                    className="bg-indigo-600 text-white text-sm px-4 py-2 rounded-lg font-semibold hover:bg-indigo-700"
                  >
                    Pay now
                  </button>
                )}
                <Link to={`/orders/${order.orderId}`} className="text-sm px-4 py-2 rounded-lg border border-gray-300 hover:bg-gray-50">
                  Details
                </Link>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
