import { useDispatch } from "react-redux";
import { useNavigate } from "react-router-dom";
import { loadRazorpayScript } from "../../utils/loadRazorpayScript";
import { createOrder } from "../../slices/orderSlice";
import { processPayment, verifyPayment } from "../../slices/paymentSlice";
import { clearCart } from "../../slices/cartSlice";

const useCheckout = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();

  // Opens Razorpay for an existing order (new or being retried) and, once Razorpay reports the
  // payment, sends it to the server for verification. Either way it then shows the order's status
  // page, which waits for the order to actually become PAID (that happens a moment later, via events).
  const payForOrder = async (order, { onPaid } = {}) => {
    try {
      // The server works out the amount from the order and refuses orders that can't be paid.
      const razorpayOrder = await dispatch(processPayment({ orderIdRef: order.orderId })).unwrap();

      if (!(await loadRazorpayScript())) {
        alert("Razorpay checkout failed to load. Check your connection and try again.");
        return;
      }

      const rzp = new window.Razorpay({
        key: import.meta.env.VITE_REACT_APP_RAZORPAY_KEY_ID,
        amount: razorpayOrder.amount,
        currency: razorpayOrder.currency,
        name: "SmartCart",
        description: `Order ${order.orderId}`,
        order_id: razorpayOrder.id,
        handler: async (response) => {
          try {
            await dispatch(
              verifyPayment({
                paymentId: response.razorpay_payment_id,
                orderId: response.razorpay_order_id,
                signature: response.razorpay_signature,
                orderIdRef: order.orderId,
              })
            ).unwrap();
            onPaid?.();
            navigate(`/orders/${order.orderId}`, { state: { justPaid: true } });
          } catch (err) {
            if (err?.status === 503) {
              // Razorpay couldn't be asked; the server will confirm the payment on its own.
              navigate(`/orders/${order.orderId}`, { state: { justPaid: true, confirmingLater: true } });
            } else {
              // The status page shows what the server recorded (usually PAYMENT_FAILED).
              navigate(`/orders/${order.orderId}`, { state: { verifyFailed: true } });
            }
          }
        },
        prefill: { name: order.user?.name, email: order.user?.email },
        theme: { color: "#4f46e5" },
      });
      rzp.on("payment.failed", (response) => {
        // Razorpay keeps its window open so the customer can try another method.
        console.warn("Razorpay payment failed:", response.error?.description);
      });
      rzp.open();
    } catch (err) {
      alert(typeof err === "string" ? err : "Could not start the payment. Please try again.");
    }
  };

  // Checkout from the cart: place the order (reserves the stock), then pay for it.
  const handlePayment = async (orderPayload) => {
    let order;
    try {
      order = await dispatch(createOrder(orderPayload)).unwrap();
    } catch (err) {
      // e.g. "Not enough stock for Mug: 2 left"
      alert(typeof err === "string" ? err : "Could not place the order. Please try again.");
      return;
    }
    // The checked-out cart is emptied once paid (the server also removes the purchased items).
    await payForOrder(order, { onPaid: () => dispatch(clearCart()) });
  };

  return { handlePayment, payForOrder };
};

export default useCheckout;
