# 后端镜像：Spring Boot 可执行 jar
# 基础镜像选 jammy(glibc) 而非 alpine(musl)——ONNX Runtime 的本地 .so 依赖 glibc
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
# 构建上下文里放 jar（upload.py 已重命名为 app.jar）
COPY app.jar app.jar
# 商品种子图片：ShopBootstrap 启动时导入 shop_image_blob 表（表为空才执行）
COPY shop_seed_images/ /app/shop_seed_images/

ENV TZ=Asia/Shanghai
# 2G 小机器，限制堆内存
ENV JAVA_TOOL_OPTIONS=-Xmx512m

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
