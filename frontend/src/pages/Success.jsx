import React from "react";
import { useLocation, useNavigate } from "react-router-dom";

export default function Success() {
  const location = useLocation();
  const navigate = useNavigate();
  const orderId = location.state?.orderId || "123456";

  return (
    <div className="min-h-screen bg-gradient-to-br from-green-50 to-emerald-100 flex items-center justify-center">
      <div className="max-w-md mx-auto px-6 py-12">
        {/* Success Icon */}
        <div className="text-center mb-8">
          <div className="mx-auto w-24 h-24 bg-green-100 rounded-full flex items-center justify-center mb-6 animate-bounce">
            <span className="text-4xl">✅</span>
          </div>

          {/* Success Message */}
          <h1 className="text-3xl font-bold text-green-800 mb-4">
            Order Successful!
          </h1>
          <p className="text-green-600 mb-8">
            Thank you for your purchase. We've received your order and will
            process it shortly.
          </p>
        </div>

        {/* Order Details Card */}
        <div className="bg-white rounded-2xl shadow-lg p-8 mb-8">
          <h2 className="text-xl font-semibold text-gray-800 mb-4">
            Order Details
          </h2>

          <div className="space-y-4">
            <div className="flex justify-between items-center">
              <span className="text-gray-600">Order ID:</span>
              <span className="font-mono font-semibold text-gray-800 bg-gray-100 px-3 py-1 rounded">
                #{orderId}
              </span>
            </div>

            <div className="flex justify-between items-center">
              <span className="text-gray-600">Order Date:</span>
              <span className="font-semibold text-gray-800">
                {new Date().toLocaleDateString()}
              </span>
            </div>

            <div className="flex justify-between items-center">
              <span className="text-gray-600">Total Amount:</span>
              <span className="font-semibold text-green-600">₹4,815</span>
            </div>
          </div>
        </div>

        {/* Next Steps */}
        <div className="bg-blue-50 border border-blue-200 rounded-xl p-6 mb-8">
          <h3 className="text-lg font-semibold text-blue-800 mb-3">
            What's Next?
          </h3>
          <ul className="space-y-2 text-sm text-blue-700">
            <li className="flex items-center gap-2">
              <span className="text-blue-500">📧</span>
              You'll receive an order confirmation email shortly
            </li>
            <li className="flex items-center gap-2">
              <span className="text-blue-500">📦</span>
              We'll ship your order within 2-3 business days
            </li>
            <li className="flex items-center gap-2">
              <span className="text-blue-500">📱</span>
              Track your order with the order ID above
            </li>
          </ul>
        </div>

        {/* Action Buttons */}
        <div className="space-y-3">
          <button
            onClick={() => navigate("/")}
            className="w-full bg-gradient-to-r from-green-600 to-emerald-600 text-white py-3 px-6 rounded-lg font-semibold hover:from-green-700 hover:to-emerald-700 transition-all duration-200 shadow-lg hover:shadow-xl"
          >
            Continue Shopping
          </button>

          <button
            onClick={() => navigate("/orders")}
            className="w-full border border-gray-300 text-gray-700 py-3 px-6 rounded-lg font-semibold hover:bg-gray-50 transition-colors duration-200"
          >
            View My Orders
          </button>
        </div>

        {/* Contact Support */}
        <div className="text-center mt-8">
          <p className="text-sm text-gray-600 mb-2">
            Need help? Contact our support team
          </p>
          <a
            href="mailto:support@smartcart.com"
            className="text-sm text-blue-600 hover:text-blue-700 font-medium"
          >
            support@smartcart.com
          </a>
        </div>
      </div>
    </div>
  );
}
