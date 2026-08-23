# syntax=docker/dockerfile:1

# --- dev: Vite 개발 서버 (HMR). compose 기본 타깃 ---
FROM node:22-alpine AS dev
WORKDIR /app
ENV NODE_ENV=development

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]

# --- build: 정적 산출물 생성 ---
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# --- prod: nginx가 정적 파일 서빙 + /api·/oauth2 백엔드 프록시 ---
# 세션 쿠키와 카카오 redirect_uri를 한 오리진으로 유지하기 위해 프록시가 필요하다.
FROM nginx:1.27-alpine AS prod
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
