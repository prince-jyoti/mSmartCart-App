import { useEffect, useState } from "react";
import { useSelector, useDispatch } from "react-redux";
import {
  addProduct,
  deleteProduct,
  editProduct,
  fetchProducts,
} from "../slices/productSlice";
import { addToCart } from "../slices/cartSlice";
import PopUp from "../utils/PopUp";
import AddProduct from "./AddProduct";

export default function Home() {
  const dispatch = useDispatch();
  const { items: products, status } = useSelector((state) => state.products);
  const user = useSelector((state) => state.auth.user);
  const isAdmin = user?.role === "ADMIN";
  const [isAddProduct, setIsAddProduct] = useState(false);
  const [isEditProduct, setIsEditProduct] = useState(false);
  const initialPayload = {
    title: "",
    description: "",
    price: "",
    image: "",
    category: "",
    brand: "",
    model: "",
    color: "",
    discount: 0,
  };
  const [form, setForm] = useState(initialPayload);
  useEffect(() => {
    dispatch(fetchProducts());
  }, [dispatch]);

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
      {/* Hero Section (smaller) */}
      <div className="bg-gradient-to-r from-indigo-600 to-purple-600 text-white py-6">
        <div className="max-w-7xl mx-auto px-6 text-center">
          <h1 className="text-3xl md:text-4xl font-bold mb-2">
            Welcome to SmartCart
          </h1>
          <p className="text-base text-indigo-100 mb-2">
            Discover amazing products at unbeatable prices
          </p>
        </div>
      </div>

      {/* Products Section */}
      <div className="max-w-7xl mx-auto px-6 py-8">
        <div className="flex justify-between items-center mb-8">
          <h2 className="text-3xl font-bold text-gray-800 text-center w-full">
            Featured Products
          </h2>
          {isAdmin && (
            <button
              className="ml-4 bg-green-600 text-white px-5 py-2 rounded-lg font-semibold hover:bg-green-700 transition-colors duration-200"
              onClick={() => setIsAddProduct(true)}
            >
              + Add Product
            </button>
          )}
        </div>
        <PopUp
          open={isAddProduct}
          onClose={() => {
            setIsAddProduct(false);
            setForm({ ...initialPayload });
          }}
          title="Add New Product"
          width="max-w-2xl"
          // height="h-[600px]"
        >
          <AddProduct
            form={form}
            setForm={setForm}
            onSubmit={() => {
              dispatch(addProduct(form));
              setIsAddProduct(false);
              setForm({ ...initialPayload });
            }}
          />
        </PopUp>
        <PopUp
          open={isEditProduct}
          onClose={() => {
            setIsEditProduct(false);
            setForm({ ...initialPayload });
          }}
          title="Edit Product"
          width="max-w-2xl"
        >
          <AddProduct
            form={form}
            setForm={setForm}
            onSubmit={() => {
              dispatch(editProduct(form));
              setIsEditProduct(false);
              setForm({ ...initialPayload });
            }}
          />
        </PopUp>
        {status === "loading" ? (
          <div className="text-center text-lg text-gray-500">
            Loading products...
          </div>
        ) : status === "failed" ? (
          <div className="text-center text-red-500">
            Failed to load products.
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-8">
            {products && products.length > 0 ? (
              products.map((product) => (
                <div
                  key={product.id}
                  className="bg-white rounded-2xl shadow-lg hover:shadow-2xl transition-all duration-300 transform hover:-translate-y-2 overflow-hidden"
                >
                  {/* Product Image */}
                  <div className="relative overflow-hidden">
                    <img
                      src={product.image}
                      alt={product.name}
                      className="w-full h-48 object-cover hover:scale-110 transition-transform duration-300"
                    />
                  </div>

                  {/* Product Info */}
                  <div className="p-6">
                    <h3 className="text-xl font-bold text-gray-800 mb-2">
                      {product.name}
                    </h3>
                    <p className="text-gray-600 text-sm mb-4">
                      {product.description}
                    </p>

                    {/* Price */}
                    <div className="flex items-center justify-between mb-4">
                      <span className="text-2xl font-bold text-indigo-600">
                        ₹{product.price}
                      </span>
                      <span className="text-sm text-gray-500 line-through">
                        ₹{product.price + 200}
                      </span>
                    </div>

                    {/* Action Buttons */}
                    <div className="flex flex-wrap gap-2">
                      <button
                        className="flex-1 min-w-[100px] bg-indigo-600 text-white py-2 px-2 rounded-md font-semibold text-sm hover:bg-indigo-700 transition-colors duration-200 flex items-center justify-center gap-1"
                        onClick={() => dispatch(addToCart(product))}
                      >
                        <span>🛒</span>
                        Add to Cart
                      </button>
                      {isAdmin && (
                        <>
                          <button
                            className="min-w-[80px] px-2 py-2 border border-yellow-600 text-yellow-600 rounded-md text-sm hover:bg-yellow-50 transition-colors duration-200"
                            onClick={() => {
                              setForm(product);
                              setIsEditProduct(true);
                            }}
                          >
                            ✏️ Edit
                          </button>
                          <button
                            className="min-w-[80px] px-2 py-2 border border-red-600 text-red-600 rounded-md text-sm hover:bg-red-50 transition-colors duration-200"
                            onClick={() => {
                              dispatch(deleteProduct(product.id))
                                .unwrap()
                                .then(() => {
                                  fetchProducts();
                                })
                                .catch((err) => {
                                  console.error(
                                    "Failed to delete product:",
                                    err
                                  );
                                });
                            }}
                          >
                            🗑️ Delete
                          </button>
                        </>
                      )}
                    </div>
                  </div>
                </div>
              ))
            ) : (
              <div className="col-span-full text-center text-gray-500">
                No products found.
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
