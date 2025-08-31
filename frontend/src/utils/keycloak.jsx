import Keycloak from "keycloak-js";

if (!window._keycloakInstance) {
  window._keycloakInstance = new Keycloak({
    url: "http://localhost:8080",
    // url: "http://keycloak:8080",
    realm: "myrealm",
    clientId: "react-frontend",
  });
  window._keycloakInstance._initialized = false;
}

export default window._keycloakInstance;
