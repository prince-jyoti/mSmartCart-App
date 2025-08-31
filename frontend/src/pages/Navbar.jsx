import { useDispatch, useSelector } from "react-redux";
import { Link } from "react-router-dom";
import { logout } from "../slices/authSlice";
import { fetchCart } from "../slices/cartSlice";
import { useEffect } from "react";
import keycloak from "../utils/keycloak";

export default function Navbar() {
  const dispatch = useDispatch();
  const { items } = useSelector((state) => state.cart);
  const cartCount = items.reduce((count, item) => count + item.quantity, 0);

  useEffect(() => {
    dispatch(fetchCart());
  }, [dispatch]);

  return (
    <nav className="bg-gradient-to-r from-indigo-600 to-purple-600 shadow-lg">
      <div className="max-w-7xl mx-auto px-6 py-4">
        <div className="flex items-center justify-between">
          {/* Logo */}
          <Link
            to="/"
            className="text-2xl font-bold text-white hover:text-indigo-200 transition-colors duration-200"
          >
            SmartCart
          </Link>

          {/* Navigation Links */}
          <div className="flex items-center gap-8">
            <Link
              to="/"
              className="text-white hover:text-indigo-200 font-medium transition-colors duration-200"
            >
              Home
            </Link>

            {/* Cart with count */}
            <Link
              to="/cart"
              className="relative text-white hover:text-indigo-200 transition-colors duration-200"
            >
              <div className="flex items-center gap-2">
                <span className="text-xl">🛒</span>
                {cartCount > 0 && (
                  <span className="absolute -top-2 -right-2 bg-red-500 text-white text-xs font-bold w-5 h-5 flex items-center justify-center rounded-full animate-pulse">
                    {cartCount}
                  </span>
                )}
              </div>
            </Link>

            {/* Optional: Add more nav items */}
            {/* <Link
              to="/products"
              className="text-white hover:text-indigo-200 font-medium transition-colors duration-200"
            >
              Products
            </Link> */}

            <button
              onClick={() => {
                if (keycloak.authenticated) {
                  keycloak.logout({ redirectUri: window.location.origin });
                  localStorage.removeItem("token");
                  dispatch(logout());
                }
              }}
              className="bg-white text-indigo-600 px-4 py-2 rounded-lg font-medium hover:bg-indigo-50 transition-colors duration-200"
            >
              Logout
            </button>
          </div>
        </div>
      </div>
    </nav>
  );
}
