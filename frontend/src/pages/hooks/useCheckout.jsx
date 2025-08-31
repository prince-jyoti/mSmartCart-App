// import axios from "axios";
import { useDispatch } from "react-redux";
import { loadRazorpayScript } from "../../utils/loadRazorpayScript";
import { createOrder } from "../../slices/orderSlice";
import { processPayment, verifyPayment } from "../../slices/paymentSlice";
import { clearCart } from "../../slices/cartSlice";
import { useNavigate } from "react-router-dom";
const useCheckout = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const handlePayment = async (orderPayload) => {
    try {
      // 1. Create order in your DB
      const orderResult = await dispatch(createOrder(orderPayload)).unwrap();

      // 2. Create Razorpay order (get razorpayOrderId, amount, currency)
      const razorpayOrder = await dispatch(
        processPayment({
          amount: orderResult.totalAmount,
          currency: "INR",
          receipt: orderResult.orderId,
        })
      ).unwrap();

      // 3. Load Razorpay SDK
      const loaded = await loadRazorpayScript();
      if (!loaded) {
        alert("Razorpay SDK failed to load");
        return;
      }

      // 4. Open Razorpay modal
      const options = {
        key: import.meta.env.VITE_REACT_APP_RAZORPAY_KEY_ID,
        amount: razorpayOrder.amount,
        currency: razorpayOrder.currency,
        name: "SmartCart",
        description: "Order Payment",
        order_id: razorpayOrder.id, // Razorpay order ID
        handler: async function (response) {
          // 5. On payment success, verify and record payment
          await dispatch(
            verifyPayment({
              paymentId: response.razorpay_payment_id,
              orderId: response.razorpay_order_id,
              signature: response.razorpay_signature,
              status: "",
              paymentDate: "",
              orderIdRef: orderResult.orderId, // your internal order ID
            })
          );
          alert("Payment successful!");
          dispatch(clearCart());
          navigate("/");
        },
        prefill: {
          name: orderResult.user.name,
          email: orderResult.user.email,
          role: orderResult.user.role,
        },
        notes: {
          address: "Your company address",
        },
        theme: {
          color: "#3466FF",
        },
      };
      const rzp = new window.Razorpay(options);
      rzp.open();
      rzp.on("payment.failed", function (response) {
        alert(response.error.description);
      });
      console.log("Order created successfully:", orderResult);
    } catch (err) {
      console.error("Order creation failed", err);
    }
  };

  return {
    handlePayment,
  };
};

export default useCheckout;
