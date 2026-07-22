# 1. 基础镜像：包含 JDK 11、Maven 以及 Playwright 浏览器依赖
FROM mcr.microsoft.com/playwright/java:v1.40.0-jammy

# 2. 设置工作目录
WORKDIR /app

# 3. 先复制 pom.xml（利用 Docker 缓存）
COPY pom.xml .

# 4. 下载项目依赖
RUN mvn dependency:go-offline -Dmaven.repo.remote=https://maven.aliyun.com/repository/public

# 5. 复制源代码
COPY src ./src

# 6. 编译 + 下载测试插件（-DskipTests = 编译但不执行）
#    test 阶段会触发 surefire 插件解析，把 surefire-testng 等 JAR 下载到本地仓库
RUN mvn clean test -DskipTests

# 7. 容器启动：只执行测试，不走完整生命周期（插件已缓存，无需重新下载）
#    docker run image                                    → 全量测试
#    docker run image mvn surefire:test -Dtest=某个类       → 指定测试类
#    docker run image mvn surefire:test -DsuiteXmlFile=... → 指定 suite
CMD ["mvn", "surefire:test"]
