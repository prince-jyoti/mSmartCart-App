import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { fetchCurrentUser } from "./slices/authSlice";
import MainRoute from "./route/MainRoute";

function App() {
  const dispatch = useDispatch();
  const authChecked = useSelector((state) => state.auth.authChecked);

  useEffect(() => {
    // The httpOnly auth_token cookie (if any) is sent automatically;
    // this call tells us whether the current session is still valid.
    dispatch(fetchCurrentUser());
  }, [dispatch]);

  if (!authChecked) return <div>Loading...</div>;
  return (
    <>
      <MainRoute />
    </>
  );
}

export default App;
