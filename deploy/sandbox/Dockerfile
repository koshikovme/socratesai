FROM judge0/judge0:1.13.1@sha256:6b5d6a66aa19a8e878a52ea3c6a560afc1086734d96e2885b561fd5c6018f082
USER root
COPY harden.rb /tmp/harden.rb
RUN ruby /tmp/harden.rb && rm /tmp/harden.rb
USER judge0
