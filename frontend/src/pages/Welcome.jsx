import { useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import { useEffect } from "react";

const Welcome = () => {
  const isAuthenticated = useSelector((state) => state.auth.isAuthenticated);
  const navigate = useNavigate();

  const handleLogin = () => {
    navigate("/auth");
  };

  useEffect(() => {
    if (isAuthenticated) {
      navigate("/", { replace: true });
    }
  }, [isAuthenticated, navigate]);

  return (
    <div className="flex items-center justify-center min-h-screen">
      <div className="text-center p-14 rounded-3xl shadow-2xl border border-gray-200 bg-gradient-to-br from-white via-blue-50 to-blue-100 max-w-xl w-full">
        <h1 className="text-4xl font-extrabold mb-8 text-blue-700 drop-shadow">
          Welcome to <span className="text-blue-400">SmartCart</span>
        </h1>
        <button
          onClick={handleLogin}
          className="px-8 py-3 bg-gradient-to-r from-blue-500 to-blue-700 text-white rounded-full shadow-lg hover:scale-105 hover:from-blue-600 hover:to-blue-800 transition-all duration-200 font-semibold text-lg"
        >
          Login
        </button>
      </div>
    </div>
  );
};

export default Welcome;
