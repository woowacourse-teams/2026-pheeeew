FROM python:3.13-alpine
RUN apk add --no-cache postgresql17-client \
    && addgroup -g 10001 observer && adduser -D -u 10001 -G observer observer \
    && mkdir /data && chown observer:observer /data && chmod 0750 /data
WORKDIR /observer
COPY collect.py store.py main.py snapshot.sql ./
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
USER 10001:10001
ENTRYPOINT ["python3", "main.py"]
