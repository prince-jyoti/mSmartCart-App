import { createSlice, createAsyncThunk } from "@reduxjs/toolkit";
import API from "../utils/api";

// Process Payment (simulate or real payment gateway)
export const processPayment = createAsyncThunk(
  "payments/create-razorpay-order",
  async ({ amount, currency, receipt }, { rejectWithValue }) => {
    try {
      // Example: POST /payments
      const response = await API.post(
        `/payments/create-razorpay-order?amount=${amount}&currency=${currency}&receipt=${receipt}`
      );
      return response.data.data;
    } catch (err) {
      return rejectWithValue(err.response?.data?.message || "Payment failed");
    }
  }
);

export const verifyPayment = createAsyncThunk(
  "payments/verifyPayment",
  async (verifyPayload, { rejectWithValue }) => {
    try {
      // Example: POST /payments/verify
      const response = await API.post("/payments", verifyPayload);
      return response.data.data;
    } catch (err) {
      return rejectWithValue(
        err.response?.data?.message || "Verification failed"
      );
    }
  }
);
const paymentSlice = createSlice({
  name: "payment",
  initialState: {
    status: "idle",
    error: null,
    paymentResult: null,
  },
  reducers: {
    resetPayment: (state) => {
      state.status = "idle";
      state.error = null;
      state.paymentResult = null;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(processPayment.pending, (state) => {
        state.status = "loading";
        state.error = null;
      })
      .addCase(processPayment.fulfilled, (state, action) => {
        state.status = "succeeded";
        state.paymentResult = action.payload;
      })
      .addCase(processPayment.rejected, (state, action) => {
        state.status = "failed";
        state.error = action.payload;
      })
      .addCase(verifyPayment.pending, (state) => {
        state.status = "loading";
        state.error = null;
      })
      .addCase(verifyPayment.fulfilled, (state, action) => {
        state.status = "succeeded";
        state.paymentResult = action.payload;
      })
      .addCase(verifyPayment.rejected, (state, action) => {
        state.status = "failed";
        state.error = action.payload;
      });
  },
});

export const { resetPayment } = paymentSlice.actions;
export default paymentSlice.reducer;
