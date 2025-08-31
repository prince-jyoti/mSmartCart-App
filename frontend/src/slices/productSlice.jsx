import { createSlice, createAsyncThunk } from "@reduxjs/toolkit";
import API from "../utils/api";

// Fetch all products
export const fetchProducts = createAsyncThunk("products/fetch", async () => {
  const response = await API.get("/products");
  console.log("Fetched Products:", response.data.data);
  return response.data.data;
});

// Add a product
export const addProduct = createAsyncThunk("products/add", async (product) => {
  const response = await API.post("/products", product);
  return response.data.data;
});

// Edit a product
export const editProduct = createAsyncThunk(
  "products/edit",
  async (product) => {
    const response = await API.put(`/products/${product.id}`, product);
    return response.data.data;
  }
);

// Delete a product
export const deleteProduct = createAsyncThunk("products/delete", async (id) => {
  await API.delete(`/products/${id}`);
  return id;
});

const productSlice = createSlice({
  name: "products",
  initialState: {
    items: [],
    status: "idle", // 'loading', 'succeeded', 'failed'
    error: null,
  },
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchProducts.pending, (state) => {
        state.status = "loading";
      })
      .addCase(fetchProducts.fulfilled, (state, action) => {
        state.status = "succeeded";
        state.items = action.payload;
      })
      .addCase(fetchProducts.rejected, (state, action) => {
        state.status = "failed";
        state.error = action.error.message;
      })
      // Add Product
      .addCase(addProduct.fulfilled, (state, action) => {
        state.items.push(action.payload);
      })
      // Edit Product
      .addCase(editProduct.fulfilled, (state, action) => {
        const idx = state.items.findIndex((p) => p.id === action.payload.id);
        if (idx !== -1) state.items[idx] = action.payload;
      })
      // Delete Product
      .addCase(deleteProduct.fulfilled, (state, action) => {
        state.items = state.items.filter((p) => p.id !== action.payload);
      });
  },
});

export default productSlice.reducer;
