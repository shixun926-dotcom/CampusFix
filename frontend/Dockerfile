# ---------- 构建阶段 ----------
FROM node:20-alpine AS build
WORKDIR /app

# 依赖层可缓存
COPY package.json package-lock.json* .npmrc* ./
RUN npm install --no-fund --no-audit

COPY . .

# 构建期注入后端地址（默认同源，由本容器的 nginx 反代 /api 与 /files）
ARG VITE_API_BASE_URL=/api/v1
ARG VITE_ASSET_BASE_URL=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_ASSET_BASE_URL=$VITE_ASSET_BASE_URL

RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.27-alpine

ENV TZ=Asia/Shanghai
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
