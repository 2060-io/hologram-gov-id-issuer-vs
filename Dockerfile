FROM node:18 as builder

WORKDIR /www
ENV RUN_MODE="docker"

COPY package.json yarn.lock ./
COPY ./packages/model/build ./packages/model/build
COPY ./packages/client/build ./packages/client/build
COPY ./packages/nestjs-client/build ./packages/nestjs-client/build

RUN yarn install

COPY tsconfig.build.json tsconfig.json ./
COPY ./src ./src

RUN yarn build

FROM node:18-alpine as runtime

WORKDIR /www
ENV NODE_ENV="production"

COPY --from=builder /www/package.json /www/yarn.lock ./
COPY --from=builder /www/build ./build

RUN yarn install --production

CMD ["node", "build/main.js"]
