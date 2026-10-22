# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e
FROM node:22.23.1-alpine@sha256:16e22a550f3863206a3f701448c45f7912c6896a62de43add43bb9c86130c3e2 AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY index.html vite.config.js ./
COPY src/ src/
COPY public/ public/
ENV VITE_API_BASE_URL="" VITE_WS_BASE_URL=""
RUN npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03 AS runtime
COPY --from=build /workspace/dist/ /usr/share/nginx/html/
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY nginx/frontend.conf.template /etc/nginx/templates/frontend.conf.template
COPY nginx/proxy.conf nginx/websocket.conf /etc/nginx/snippets/
COPY nginx/start.sh /opt/start.sh
USER 101:101
EXPOSE 8080 8443
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 CMD ["wget", "-q", "--spider", "http://127.0.0.1:8080/healthz"]
ENTRYPOINT ["/bin/sh", "/opt/start.sh"]
