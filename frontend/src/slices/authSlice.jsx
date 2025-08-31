import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";
import API from "../utils/api";

const initialState = {
  isAuthenticated: false,
  user: {
    token: null,
    username: null,
    email: null,
    role: null,
  },
};
console.log("authSlice initialized with state:", initialState);
export const CreateOrUpdateUser = createAsyncThunk("createUser", async () => {
  const response = await API.post("/user");
  // return response.data.data;
  console.log("CreateOrUpdateUser response:", response.data.data);
  return response.data.data;
});
const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    login(state, action) {
      state.isAuthenticated = true;
      state.user = {
        token: action.payload.token,
        username: action.payload.username,
        email: action.payload.email,
        role: action.payload.role,
      };
    },
    logout(state) {
      state.isAuthenticated = false;
      state.user = {
        token: null,
        username: null,
        email: null,
        role: null,
      };
    },
  },
  // extraReducers: (builder) => {
  //   builder.addCase(logoutUser.fulfilled, (state) => {
  //     state.isAuthenticated = false;
  //     state.user = null;
  //   });
  // },
});
export const { login, logout } = authSlice.actions;
export default authSlice.reducer;
