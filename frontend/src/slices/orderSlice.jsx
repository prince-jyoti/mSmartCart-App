import { createSlice, createAsyncThunk } from "@reduxjs/toolkit";
import API from "../utils/api";

// 1. Create Order (status: PENDING)
export const createOrder = createAsyncThunk(
  "order/createOrder",
  async (orderData, { rejectWithValue }) => {
    try {
      const response = await API.post("/orders", orderData);
      return response.data.data; // should include order id, etc.
    } catch (err) {
      return rejectWithValue(err.response?.data?.message || "Order creation failed");
    }
  }
);

// 2. Update Order Payment (status: PAID or FAILED)
export const updateOrderPayment = createAsyncThunk(
  "order/updateOrderPayment",
  async ({ orderId, payment }, { rejectWithValue }) => {
    try {
      const response = await API.patch(`/orders/${orderId}/payment`, payment);
      return response.data.data;
    } catch (err) {
      return rejectWithValue(err.response?.data?.message || "Payment update failed");
    }
  }
);

const orderSlice = createSlice({
  name: "order",
  initialState: {
    order: null,
    status: "idle",
    error: null,
    paymentStatus: null,
  },
  reducers: {
    resetOrder: (state) => {
      state.order = null;
      state.status = "idle";
      state.error = null;
      state.paymentStatus = null;
    },
  },
  extraReducers: (builder) => {
    builder
      // Create Order
      .addCase(createOrder.pending, (state) => {
        state.status = "loading";
        state.error = null;
      })
      .addCase(createOrder.fulfilled, (state, action) => {
        state.status = "succeeded";
        state.order = action.payload;
        state.error = null;
      })
      .addCase(createOrder.rejected, (state, action) => {
        state.status = "failed";
        state.error = action.payload;
      })
      // Update Payment
      .addCase(updateOrderPayment.pending, (state) => {
        state.paymentStatus = "loading";
      })
      .addCase(updateOrderPayment.fulfilled, (state, action) => {
        state.paymentStatus = "succeeded";
        state.order = { ...state.order, ...action.payload };
      })
      .addCase(updateOrderPayment.rejected, (state, action) => {
        state.paymentStatus = "failed";
        state.error = action.payload;
      });
  },
});

export const { resetOrder } = orderSlice.actions;
export default orderSlice.reducer;
