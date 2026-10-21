# 前端镜像：纯静态文件 + nginx
FROM nginx:stable

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/ /usr/share/nginx/html/

EXPOSE 80
