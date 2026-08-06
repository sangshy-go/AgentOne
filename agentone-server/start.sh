#!/bin/bash
# AgentOne 一键启动脚本
# 用法: ./start.sh [--skip-build]

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Java 17
export JAVA_HOME=/usr/local/opt/openjdk@17
export PATH="/usr/local/opt/openjdk@17/bin:$PATH"

echo "=========================================="
echo "  AgentOne 一键启动"
echo "=========================================="

# 1. 检查 Java 版本
echo ""
echo "[1/4] 检查 Java 版本..."
java -version 2>&1 | head -1

# 2. 启动 Docker 基础设施
echo ""
echo "[2/4] 检查 Docker 基础设施..."

# PostgreSQL
if docker ps --filter name=agentone-postgres --format "{{.Status}}" | grep -q "Up"; then
    echo "  ✅ PostgreSQL 已运行"
else
    echo "  🔄 启动 PostgreSQL..."
    cd docker
    POSTGRES_PORT=5433 docker compose up -d postgres
    cd ..
    # 等待就绪
    for i in {1..15}; do
        if docker exec agentone-postgres pg_isready -U agentone >/dev/null 2>&1; then
            break
        fi
        sleep 1
    done
    # 安装扩展
    docker exec agentone-postgres psql -U agentone -d agentone -c \
        "CREATE EXTENSION IF NOT EXISTS vector; CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";" 2>/dev/null || true
    echo "  ✅ PostgreSQL 就绪"
fi

# Redis
if docker ps --filter name=redis --format "{{.Names}}" | grep -q "redis"; then
    echo "  ✅ Redis 已运行"
else
    echo "  ⚠️  Redis 未运行，请手动启动"
fi

# 3. 编译
if [ "$1" = "--skip-build" ]; then
    echo ""
    echo "[3/4] 跳过编译 (--skip-build)"
else
    echo ""
    echo "[3/4] 编译项目..."
    mvn clean package -DskipTests -q
    echo "  ✅ 编译完成"
fi

# 4. 启动应用
echo ""
echo "[4/4] 启动 Spring Boot..."

# 停止已有进程
if lsof -ti:8080 >/dev/null 2>&1; then
    echo "  🔄 停止旧进程..."
    kill $(lsof -ti:8080) 2>/dev/null
    sleep 2
fi

java -jar agentone-api/target/agentone-api-1.0.0-SNAPSHOT.jar \
    --spring.datasource.url="jdbc:postgresql://localhost:5433/agentone?stringtype=unspecified" &

APP_PID=$!

# 等待启动
echo "  等待应用就绪..."
for i in {1..30}; do
    if curl -s http://localhost:8080/actuator/health 2>/dev/null | grep -q "UP"; then
        echo ""
        echo "=========================================="
        echo "  ✅ AgentOne 启动成功！"
        echo "=========================================="
        echo ""
        echo "  🌐 管理后台:  http://localhost:8080"
        echo "  🏥 健康检查:  http://localhost:8080/actuator/health"
        echo "  📡 外部 API:  http://localhost:8080/v1/health"
        echo ""
        echo "  PID: $APP_PID"
        echo "  停止: kill $APP_PID"
        echo ""
        exit 0
    fi
    sleep 2
done

echo "  ❌ 启动超时，请检查日志"
exit 1
