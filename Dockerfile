FROM node:22-alpine AS builder

WORKDIR /www
RUN corepack enable
COPY package.json pnpm-lock.yaml .npmrc ./
RUN pnpm install --frozen-lockfile
COPY tsconfig.json tsconfig.build.json nest-cli.json ./
COPY ./src ./src
RUN pnpm build

FROM node:22-alpine

WORKDIR /www
ENV RUN_MODE="docker" \
    NODE_ENV="production"

RUN corepack enable
COPY package.json pnpm-lock.yaml .npmrc ./
RUN pnpm install --prod --frozen-lockfile
COPY --from=builder /www/build ./build

CMD ["node", "build/main"]
