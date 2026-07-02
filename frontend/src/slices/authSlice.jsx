import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";
import API from "../utils/api";

const initialState = {
  isAuthenticated: false,
  authChecked: false,
  user: {
    username: null,
    email: null,
    role: null,
  },
};

// Called on app boot to check whether the httpOnly auth_token cookie
// still represents a valid session.
export const fetchCurrentUser = createAsyncThunk(
  "auth/fetchCurrentUser",
  async (_, { rejectWithValue }) => {
    try {
      const response = await API.get("/user/me");
      return response.data.data;
    } catch (err) {
      return rejectWithValue(err.response?.data?.message || "Not authenticated");
    }
  }
);

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    login(state, action) {
      state.isAuthenticated = true;
      state.user = {
        username: action.payload.name,
        email: action.payload.email,
        role: action.payload.role,
      };
    },
    logout(state) {
      state.isAuthenticated = false;
      state.user = {
        username: null,
        email: null,
        role: null,
      };
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchCurrentUser.fulfilled, (state, action) => {
        state.isAuthenticated = true;
        state.authChecked = true;
        state.user = {
          username: action.payload.name,
          email: action.payload.email,
          role: action.payload.role,
        };
      })
      .addCase(fetchCurrentUser.rejected, (state) => {
        state.isAuthenticated = false;
        state.authChecked = true;
      });
  },
});
export const { login, logout } = authSlice.actions;
export default authSlice.reducer;
