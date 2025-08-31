import axios from "axios";

const API = axios.create({
  // baseURL: "http://localhost:5177/api",  //.net
  // baseURL: "http://localhost:8083", //gateway-service //java
  baseURL: "http://gateway-service:8083", //keycloak
  // baseURL: "http://localhost:5000/api", //node.js
});
API.interceptors.request.use((config) => {
  const token = localStorage.getItem("token"); // Retrieve JWT token from storage
  if (token) {
    config.headers.Authorization = `Bearer ${token}`; // Attach token to request headers
  }
  return config;
});
export default API;
