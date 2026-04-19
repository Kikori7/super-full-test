# 1. 基础镜像：使用微软官方提供的 Playwright Java 镜像
# 包含 JDK 11、Maven 以及 Playwright 运行所需的所有系统依赖
FROM mcr.microsoft.com/playwright/java:v1.40.0-jammy

# 2. 设置工作目录
WORKDIR /app

# 3. 先复制 pom.xml (利用 Docker 缓存机制)
# 如果 pom.xml 没变，Docker 会直接使用缓存的依赖层，极大加快构建速度
COPY pom.xml .

# 4. 下载依赖
# 这一步会下载 Maven 依赖和 Playwright 浏览器（如果在基础镜像里还没装好）
RUN mvn dependency:go-offline

# 5. 复制源代码
COPY src ./src
COPY testng.xml .

# 6. 编译打包
RUN mvn clean package -DskipTests

# 7. 容器启动命令：运行 TestNG 测试
# 测试报告通常会生成在 target/surefire-reports 或 target/allure-results
CMD ["mvn", "test"]