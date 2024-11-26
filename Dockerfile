FROM node:22 as base

WORKDIR /www
ENV RUN_MODE="docker"

COPY package.json yarn.lock ./

RUN yarn install

COPY tsconfig.json tsconfig.build.json nest-cli.json ./
COPY ./src ./src

RUN yarn build
CMD yarn start
