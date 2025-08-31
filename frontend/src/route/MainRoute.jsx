import { Route, BrowserRouter as Router, Routes } from "react-router-dom";
// import Login from "../pages/Login";
import ProtectedRoute from "./ProtectedRoute";
import Home from "../pages/Product";
import Welcome from "../pages/Welcome";
import Cart from "../pages/Cart";
import Checkout from "../pages/Checkout";
import Success from "../pages/Success";
import Layout from "../pages/Layout";

const MainRoute = () => {
  return (
    <Router>
      <Routes>
        <Route path="/login" element={<Welcome />} />
        {/* <Route path="/login" element={<Login onLogin={onLogin} />} /> */}
        <Route element={<ProtectedRoute />}>
          <Route path="/" element={<Layout />}>
            <Route index element={<Home />} />
            <Route path="cart" element={<Cart />} />
            <Route path="checkout" element={<Checkout />} />
            <Route path="success" element={<Success />} />
          </Route>
        </Route>
      </Routes>
    </Router>
  );
};
export default MainRoute;
