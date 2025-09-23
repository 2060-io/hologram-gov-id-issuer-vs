FROM node:22-alpine

WORKDIR /www
ENV RUN_MODE="docker"

RUN corepack enable
COPY package.json pnpm-lock.yaml .npmrc ./

RUN pnpm install

COPY tsconfig.json tsconfig.build.json nest-cli.json ./
COPY ./src ./src

RUN pnpm build
CMD pnpm start
