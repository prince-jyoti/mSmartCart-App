import axios from "axios";

const API = axios.create({
  baseURL: "http://localhost:8083", //gateway-service
  withCredentials: true, // send the httpOnly auth_token cookie automatically
});
export default API;
