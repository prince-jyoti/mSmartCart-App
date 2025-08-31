import { createSlice, createAsyncThunk } from "@reduxjs/toolkit";
import API from "../utils/api";

const initialState = {
  items: [],
  total: 0,
  status: "idle",
  error: null,
};

// Fetch cart items
export const fetchCart = createAsyncThunk("cart/fetch", async () => {
  const response = await API.get("/carts");
  return response.data.data;
});

// Add to cart
export const addToCart = createAsyncThunk("cart/add", async (item) => {
  const response = await API.post("/carts/add", {
    productId: item.id,
    id: item.id,
    cartId: item.cartId,
    quantity: item.quantity,
    total: item.price,
    title: item.title,
    description: item.description,
    image: item.image,
    price: item.price,
    stock: item.stock,
    brand: item.brand,
    model: item.model,
    color: item.color,
    category: item.category,
    discount: item.discount,
  });
  return response.data.data;
});
//Update cart item quantity
export const updateCartItem = createAsyncThunk("cart/update", async (item) => {
  const response = await API.put(`/carts/update`, item);
  return response.data.data;
});
// Remove from cart
export const removeFromCart = createAsyncThunk("carts/remove", async (id) => {
  await API.delete(`/carts/remove/${id}`);
  return id;
});

// Clear cart
export const clearCart = createAsyncThunk("carts/clear", async () => {
  await API.delete("/carts/clear");
  return;
});

const cartSlice = createSlice({
  name: "cart",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      // Fetch cart
      .addCase(fetchCart.pending, (state) => {
        state.status = "loading";
      })
      .addCase(fetchCart.fulfilled, (state, action) => {
        state.status = "succeeded";
        state.items = action.payload.items;
        state.total = action.payload.totalPrice;
      })
      .addCase(fetchCart.rejected, (state, action) => {
        state.status = "failed";
        state.error = action.error.message;
      })
      // Add to cart
      .addCase(addToCart.fulfilled, (state, action) => {
        state.items = action.payload.items;
        state.total = action.payload.totalPrice;
      })
      // Update cart item
      .addCase(updateCartItem.fulfilled, (state, action) => {
        state.items = action.payload.items;
        state.total = action.payload.totalPrice;
        // const idx = state.items.findIndex((i) => i.id === action.payload.id);
        // if (idx !== -1) {
        //   state.items[idx] = action.payload;
        // }
        // state.total = state.items.reduce(
        //   (sum, i) => sum + i.price * i.quantity,
        //   0
        // );
      })
      // Remove from cart
      .addCase(removeFromCart.fulfilled, (state, action) => {
        state.items = action.payload.items;
        state.total = action.payload.totalPrice;
        // state.items = state.items.filter((i) => i.id !== action.payload);
        // state.total = state.items.reduce(
        //   (sum, i) => sum + i.price * i.quantity,
        //   0
        // );
      })
      // Clear cart
      .addCase(clearCart.fulfilled, (state) => {
        state.items = [];
        state.total = 0;
      });
  },
});

export default cartSlice.reducer;
