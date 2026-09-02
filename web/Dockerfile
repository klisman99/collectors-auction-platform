FROM node:24.20.0-alpine AS build

WORKDIR /workspace

RUN corepack enable \
    && corepack prepare pnpm@11.19.0 --activate

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY codegen/package.json codegen/package.json
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm run build

FROM nginx:1.30.4-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80
