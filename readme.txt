TimberSage（项目根目录）说明

一、本文件夹作用
- 本文件夹为「智筑云览（基于智能技术的 3D 古建筑展示平台）」的项目根目录。
- 包含前端（Vue 3 + Vite + Three.js 等）、后端（Spring Boot + Java 17 + JWT 等）以及 Docker Compose 一键启动/部署所需的配置与文档。

二、根目录内容说明（与本 readme.txt 同级）

【文件夹】
- .github/
  - GitHub 相关配置与脚本（例如自动化脚本、升级/工具记录 hook 等）。

- frontend/
  - 前端子项目（源码、构建配置、Docker/Nginx 配置等）。

- backend/
  - 后端子项目（Spring Boot 源码、Maven 配置、Docker 构建等）。

- （若存在）名称为乱码或异常长中文的文件夹
  - 多为解压/测试残留的临时目录；如确认不被项目引用，可按需清理或忽略。

【文件】
- README.md
  - 项目主说明文档（功能简介、环境要求、启动方式、常见问题等）。

- docker-compose.yml
  - Docker Compose 编排文件：启动 MySQL / Redis / MinIO / 后端 / 前端，并配置服务间连接参数。

- DOCKER_DEPLOY.md
  - 部署指南文档（偏服务器端：SSH + Docker Compose、离线镜像、数据卷、端口/安全组等）。

- database.md
  - 数据库设计文档（表结构与关系）。以**后端 entity 定义**为准。

- .env.example
  - 环境变量模板：用于生成实际 `.env`。

- .env
  - 实际环境变量文件（通常包含密钥/口令等敏感信息）。不建议提交到版本库或对外分享。

三、frontend/（前端）目录说明

【目录】
- frontend/src/
  - 前端业务源码目录。
  - 当前项目主要结构：
    - api/：接口请求封装（与后端 API 对接）。
    - assets/：静态资源（图片、样式等）。
    - components/：通用/可复用组件。
    - composables/：组合式函数（hooks）。
    - config/：业务配置、常量配置等。
    - router/：路由配置。
    - stores/：状态管理（Pinia）。
    - utils/：工具方法。
    - views/：页面级视图（路由页面）。
    - App.vue：根组件。
    - main.js：应用入口（创建应用并挂载）。

- frontend/scripts/
  - 辅助脚本目录（具体作用以目录内脚本为准）。

- frontend/dist/
  - 构建输出目录（`npm run build` / `vite build` 生成），一般不建议提交到仓库。

- frontend/node_modules/
  - 依赖安装目录（`npm install` 生成），不建议提交到仓库或打包分发。

- frontend/frontend/
  - 目录内存在嵌套同名文件夹；其用途以该目录内部实际内容为准（若未被引用，可能为历史遗留目录）。

【关键文件】
- frontend/package.json
  - 前端依赖与脚本入口（`dev`、`build`、`lint`、`format` 等）。

- frontend/package-lock.json
  - npm 依赖锁文件，用于锁定依赖版本、保证一致性。

- frontend/index.html
  - Vite 入口 HTML 模板。

- frontend/vite.config.js
  - Vite 配置（开发服务器、代理、构建配置等）。

- frontend/nginx.conf
  - 前端生产镜像内 Nginx 配置（常用于反向代理 `/api`、`/minio`，以及 SSE 等）。

- frontend/Dockerfile
  - 前端镜像构建脚本（通常为多阶段构建：先构建静态资源，再由 Nginx 提供服务）。

- frontend/.env.development
  - 开发环境变量（本地 `npm run dev` 使用）。

- frontend/.env.production
  - 生产构建环境变量（构建时使用）。

- frontend/.eslintrc.cjs
  - ESLint 配置。

- frontend/.gitignore
  - 前端子项目忽略规则（常见为 `node_modules/`、`dist/` 等）。

- frontend/.dockerignore
  - Docker 构建忽略规则（用于减少构建上下文体积）。

四、backend/（后端）目录说明

【目录】
- backend/src/
  - 后端源码目录。
  - 常用路径：
    - backend/src/main/java/
      - Java 源码目录（按包组织，例如 `com.arch3d` 下的 controller/service/repository/entity 等）。
    - backend/src/main/resources/
      - 资源与配置目录（例如 `application.yml`）。

- backend/target/
  - Maven 构建输出目录（编译产物、测试报告等），不建议提交到仓库或打包分发。

- backend/.idea/
  - IntelliJ IDEA 工程配置目录（是否提交到仓库取决于团队规范；通常不建议）。

【关键文件】
- backend/pom.xml
  - Maven 项目配置与依赖声明（Spring Boot、JPA、Security、Redis、MinIO 等）。

- backend/src/main/resources/application.yml
  - 后端核心配置文件（数据源、Redis、MinIO、JWT、CORS 等；也可由环境变量覆盖）。

- backend/Dockerfile
  - 后端镜像构建脚本（编译/打包并生成运行镜像）。

- backend/.dockerignore
  - Docker 构建忽略规则（减少构建上下文体积）。

- backend/error.log
  - 日志文件（若为运行/调试产生，可按需清理；不建议提交到仓库或对外分发）。

