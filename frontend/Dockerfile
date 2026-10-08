# ---- Build Stage ----
FROM node:22 AS build
WORKDIR /app

# Copy package.json and package-lock.json
COPY package*.json ./
# Cache mount: npm packages are downloaded once and reused across builds.
RUN --mount=type=cache,target=/root/.npm npm install

# Copy the rest of the frontend source
COPY . .
RUN npm run build

# ---- Nginx Runtime ----
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]