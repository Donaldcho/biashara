FROM python:3.13-slim

WORKDIR /service
COPY enterprise_sync_server.py /service/enterprise_sync_server.py

ENV BIASHARA_ENTERPRISE_HOST=0.0.0.0
ENV BIASHARA_ENTERPRISE_PORT=8088
ENV BIASHARA_ENTERPRISE_DB=/data/enterprise_sync.db

VOLUME ["/data"]
EXPOSE 8088

CMD ["python", "/service/enterprise_sync_server.py"]
