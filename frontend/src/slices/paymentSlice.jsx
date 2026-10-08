import { createSlice, createAsyncThunk } from "@reduxjs/toolkit";
import API from "../utils/api";

// Creates the Razorpay order for one of our orders. The server works out the amount
// from the order itself, so only the order id is sent.
export const processPayment = createAsyncThunk(
  "payments/create-razorpay-order",
  async ({ orderIdRef }, { rejectWithValue }) => {
    try {
      const response = await API.post("/payments/create-razorpay-order", null, {
        params: { orderIdRef },
      });
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
      const response = await API.post("/payments", verifyPayload);
      return response.data.data;
    } catch (err) {
      // Keep the status: 503 means "paid, but not confirmed with Razorpay yet" (the server
      // confirms it later on its own), not "payment failed".
      return rejectWithValue({
        status: err.response?.status,
        message: err.response?.data?.message || "Verification failed",
      });
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
