import axios from "axios";
import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import SignUp from "./SignUp";

export default function Login({ onLogin }) {
  const [loginData, setLoginData] = useState({
    email: "",
    password: "",
  });
  const [signUpPage, setSignUpPage] = useState(false);
  const navigate = useNavigate();

  const handleChange = (e) => {
    const { name, value } = e.target;
    setLoginData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    try {
      const response = await axios.post("http://localhost:8080/auth/login", {
        email: loginData.email,
        password: loginData.password,
      });
      onLogin(response.data);
      navigate("/");
    } catch (err) {
      console.error(
        err.response?.data?.message || "Invalid username or password"
      );
    }
  };

  return (
    <div className="flex justify-center items-center min-h-screen bg-gray-100">
      <div className="bg-white p-10 rounded-xl shadow w-full max-w-md">
        <div className="flex mb-8">
          <button
            className={`flex-1 py-2 rounded-l-lg font-semibold transition ${
              !signUpPage
                ? "bg-blue-600 text-white"
                : "bg-gray-200 text-gray-700 hover:bg-blue-100"
            }`}
            onClick={() => setSignUpPage(false)}
          >
            Login
          </button>
          <button
            className={`flex-1 py-2 rounded-r-lg font-semibold transition ${
              signUpPage
                ? "bg-blue-600 text-white"
                : "bg-gray-200 text-gray-700 hover:bg-blue-100"
            }`}
            onClick={() => setSignUpPage(true)}
          >
            Sign Up
          </button>
        </div>
        {!signUpPage ? (
          <form onSubmit={handleLogin}>
            <h2 className="text-2xl font-bold mb-6 text-left text-gray-800">
              Login
            </h2>
            <div className="mb-4">
              <label
                className="block text-left text-gray-700 mb-2"
                htmlFor="email"
              >
                Email
              </label>
              <input
                id="email"
                type="text"
                className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="Enter your email"
                autoComplete="email"
                name="email"
                onChange={handleChange}
                value={loginData.email}
              />
            </div>
            <div className="mb-6">
              <label
                className="block text-left text-gray-700 mb-2"
                htmlFor="password"
              >
                Password
              </label>
              <input
                id="password"
                type="password"
                name="password"
                className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="Enter your password"
                autoComplete="current-password"
                onChange={handleChange}
                value={loginData.password}
              />
            </div>
            <button
              type="submit"
              className="w-full mb-2 bg-green-600 text-white py-2 rounded-lg font-semibold hover:bg-green-700 transition"
            >
              Login
            </button>
          </form>
        ) : (
          <div>
            <h2 className="text-2xl font-bold mb-6 text-left text-gray-800">
              Sign Up
            </h2>
            <SignUp setSignUpPage={setSignUpPage} />
          </div>
        )}
      </div>
    </div>
  );
}
