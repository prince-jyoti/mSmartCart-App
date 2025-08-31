import { useSelector } from "react-redux";
import useCheckout from "./hooks/useCheckout";

export default function Checkout() {
  const cart = useSelector((state) => state.cart);

  const { handlePayment } = useCheckout();
  const orderPayload = {
    totalAmount: cart.total,
    status: "PENDING",
    items: cart.items,
    createdAt: new Date(),
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-indigo-100 to-purple-100 flex items-center justify-center p-4">
      <div className="w-full max-w-xl mx-auto">
        <div className="bg-white rounded-3xl shadow-2xl p-10 border border-indigo-100">
          <h2 className="text-3xl font-extrabold text-indigo-700 mb-6 text-center tracking-tight drop-shadow-sm">
            Order Summary
          </h2>
          <div className="space-y-6 mb-8">
            {cart?.items && cart?.items?.length > 0 ? (
              <div className="divide-y divide-gray-200">
                {cart.items.map((item) => (
                  <div key={item.id} className="flex items-center py-4 gap-4">
                    <img
                      src={item.image}
                      alt={item.title}
                      className="w-16 h-16 object-cover rounded-xl border border-gray-200 shadow-sm"
                    />
                    <div className="flex-1">
                      <h4 className="font-semibold text-lg text-gray-800">
                        {item.title}
                      </h4>
                      <p className="text-sm text-gray-500">
                        Qty: {item.quantity}
                      </p>
                    </div>
                    <span className="font-bold text-indigo-600 text-lg">
                      ₹{item.price * item.quantity}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-gray-400 text-center py-8">
                No items in cart.
              </div>
            )}
          </div>
          <div className="rounded-xl bg-gradient-to-r from-indigo-50 to-purple-50 p-6 mb-6 border border-indigo-100">
            <div className="flex justify-between mb-2">
              <span className="text-gray-600 font-medium">Subtotal</span>
              <span className="font-semibold">₹{cart.total}</span>
            </div>
            <div className="flex justify-between mb-2">
              <span className="text-gray-600 font-medium">Shipping</span>
              <span className="font-semibold">₹99</span>
            </div>
            <div className="flex justify-between mb-2">
              <span className="text-gray-600 font-medium">Tax (18%)</span>
              <span className="font-semibold">
                ₹{Math.round(cart.total * 0.18)}
              </span>
            </div>
            <hr className="my-3 border-gray-200" />
            <div className="flex justify-between items-center text-xl font-bold">
              <span>Total</span>
              <span className="text-purple-700">
                ₹{cart.total + 99 + Math.round(cart.total * 0.18)}
              </span>
            </div>
          </div>
          <div className="flex items-center justify-center gap-2 bg-green-50 border border-green-200 rounded-xl p-4 mt-4">
            <span className="text-green-600 text-2xl">🔒</span>
            <span className="text-green-700 font-medium text-sm">
              Secure checkout powered by{" "}
              <span className="font-bold">SSL encryption</span>
            </span>
          </div>
          <button
            type="button"
            onClick={(e) => {
              e.preventDefault();
              handlePayment(orderPayload);
            }}
            className="w-full mt-8 bg-gradient-to-r from-indigo-600 to-purple-600 text-white py-4 px-6 rounded-xl font-bold text-lg shadow-lg hover:from-indigo-700 hover:to-purple-700 transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2"
          >
            Pay &amp; Place Order
          </button>
        </div>
      </div>
    </div>
  );
}
