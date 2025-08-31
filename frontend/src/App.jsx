import { useEffect, useRef, useState } from "react";
import { useDispatch } from "react-redux";
import keycloak from "./utils/keycloak";
import { CreateOrUpdateUser, login } from "./slices/authSlice";
import MainRoute from "./route/MainRoute";

function App() {
  const dispatch = useDispatch();
  const [initialized, setInitialized] = useState(false);
  const initCalled = useRef(false);

  useEffect(() => {
    if (!initCalled.current && !keycloak.authenticated) {
      initCalled.current = true;
      keycloak
        .init({
          onLoad: "check-sso",
          pkceMethod: "S256",
          // scope: "openid email profile api-gateway-audience",
        })
        .then((authenticated) => {
          if (authenticated) {
            const token = keycloak.token;
            const user = keycloak.tokenParsed;
            const roles = user?.realm_access?.roles || [];
            // For a single role (e.g., first custom role, ignoring default ones):
            const customRoles = roles.filter(
              (role) =>
                !role.startsWith("default-roles") &&
                role !== "offline_access" &&
                role !== "uma_authorization"
            );
            const role = customRoles[0] || null; // or join them if you want all roles

            const payload = {
              token: keycloak.token,
              username: user.preferred_username,
              email: user.email,
              role, // or roles: customRoles if you want all
            };

            console.log("Authenticated user:", payload);
            localStorage.setItem("token", token);
            dispatch(login(payload));
            console.log("Dispatching first CreateOrUpdateUser");
            dispatch(CreateOrUpdateUser());
          }
          setInitialized(true);
        });
    } else {
      // If already authenticated (e.g., after refresh), hydrate Redux state
      if (keycloak.authenticated && keycloak.tokenParsed) {
        dispatch(
          login({
            token: keycloak.token,
            username: keycloak.tokenParsed.preferred_username,
            email: keycloak.tokenParsed.email,
            role:
              keycloak.tokenParsed?.realm_access?.roles.find(
                (role) =>
                  !role.startsWith("default-roles") &&
                  role !== "offline_access" &&
                  role !== "uma_authorization"
              ) || null,
          })
        );
        console.log("Dispatching first CreateOrUpdateUser");
        dispatch(CreateOrUpdateUser());
      }
      setInitialized(true);
    }
  }, [dispatch]);

  if (!initialized) return <div>Loading...</div>;
  return (
    <>
      <MainRoute />
    </>
  );
}

export default App;
