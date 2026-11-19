FROM ruby:3.4-slim

RUN apt-get update \
    && apt-get install --no-install-recommends -y build-essential \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY Gemfile Gemfile.lock ./
RUN bundle config set deployment true && bundle install
COPY app.rb config.ru ./
COPY strategies ./strategies
COPY views ./views
COPY public ./public

EXPOSE 4567
USER 10001:10001
CMD ["bundle", "exec", "puma", "-b", "tcp://0.0.0.0:4567"]
