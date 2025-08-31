import axios from "axios";
import React, { useState } from "react";

// const roles = [
//   { value: "USER", label: "User" },
//   { value: "ADMIN", label: "Admin" },
// ];
const SignUp = ({ setSignUpPage }) => {
  const [signupData, setSignupData] = useState({
    name: "",
    email: "",
    password: "",
  });
  const [signupError, setSignupError] = useState("");
  const [signupSuccess, setSignupSuccess] = useState("");

  const handleChange = (e) => {
    setSignupData({ ...signupData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!signupData.name || !signupData.email || !signupData.password) {
      setSignupError("All fields are required.");
      setSignupSuccess("");
    } else {
      // e.preventDefault();
      try {
        const response = await axios.post(
          "http://localhost:8080/auth/register",
          {
            name: signupData.name,
            email: signupData.email,
            password: signupData.password,
          }
        );
        const { status } = response;
        if (status === 200) {
          setSignupSuccess("Signup successful!");
          setSignupError("");
          setSignUpPage(false);
        }
        // navigate("/");
      } catch (err) {
        console.error(
          err.response?.data?.message || "Invalid username or password"
        );
      }
    }
  };
  return (
    <div>
      {/* <h2 className="text-2xl font-bold mb-6 text-left text-gray-800">
        Sign Up
      </h2> */}
      <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
        <div>
          <label
            className="block text-gray-700 mb-1 text-left"
            htmlFor="signup-username"
          >
            Name
          </label>
          <input
            id="signup-name"
            name="name"
            type="text"
            value={signupData.name}
            onChange={handleChange}
            required
            className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label
            className="block text-gray-700 mb-1 text-left"
            htmlFor="signup-email"
          >
            Email
          </label>
          <input
            id="signup-email"
            name="email"
            type="email"
            value={signupData.email}
            onChange={handleChange}
            required
            className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label
            className="block text-gray-700 mb-1 text-left"
            htmlFor="signup-role"
          >
            Password
          </label>
          <input
            id="signup-password"
            name="password"
            type="password"
            value={signupData.password}
            onChange={handleChange}
            required
            className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          {/* <select
            id="signup-role"
            name="role"
            value={signupData.password}
            onChange={handleChange}
            required
            className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">Select a role</option>
            {roles.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select> */}
        </div>
        {signupError && (
          <div className="text-red-600 text-sm">{signupError}</div>
        )}
        {signupSuccess && (
          <div className="text-green-600 text-sm">{signupSuccess}</div>
        )}
        <button
          type="submit"
          className="w-full bg-green-600 text-white py-2 rounded-lg font-semibold hover:bg-green-700 transition"
        >
          Sign Up
        </button>
      </form>
    </div>
  );
};

export default SignUp;
