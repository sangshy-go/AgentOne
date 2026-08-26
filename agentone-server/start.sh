#!/bin/bash
# AgentOne 一键启动脚本
# 用法: ./start.sh [--build]
#   默认：jar 存在且源码未更新则跳过编译；--build 强制重新编译
#
# 流程：加载 .env → 清理残留端口 → Docker 基础设施 → 后端 → 前端
# 端口/凭据以根目录 .env 为准（API_PORT/WEB_PORT/POSTGRES_PORT/REDIS_PORT/JWT_SECRET）

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 加载根目录 .env 并导出（JWT_SECRET 等必须进子进程环境）
ENV_FILE="$SCRIPT_DIR/../.env"
if [ -f "$ENV_FILE" ]; then
    set -a
    . "$ENV_FILE"
    set +a
fi
API_PORT=${API_PORT:-8081}
WEB_PORT=${WEB_PORT:-8090}
POSTGRES_PORT=${POSTGRES_PORT:-5432}
REDIS_PORT=${REDIS_PORT:-6380}

# Java 17
export JAVA_HOME=/usr/local/opt/openjdk@17
export PATH="/usr/local/opt/openjdk@17/bin:$PATH"

echo "=========================================="
echo "  AgentOne 一键启动"
echo "  后端 :$API_PORT | 前端 :$WEB_PORT | PG :$POSTGRES_PORT | Redis :$REDIS_PORT"
echo "=========================================="

# ----------------------------------------------------------
# 1. 清理本项目端口上的残留进程（上次会话的僵尸后端/vite）
# ----------------------------------------------------------
echo ""
echo "[1/6] 清理残留端口..."
free_port() {
    local pids
    pids=$(lsof -ti:"$1" 2>/dev/null || true)
    [ -z "$pids" ] && { echo "  ✅ 端口 $1 空闲"; return; }
    echo "  🔄 清理端口 $1 上的进程 (PID: $pids)"
    kill $pids 2>/dev/null || true
    for _ in {1..5}; do lsof -ti:"$1" >/dev/null 2>&1 || return 0; sleep 1; done
    kill -9 $pids 2>/dev/null || true
}
free_port "$API_PORT"
free_port "$WEB_PORT"

# ----------------------------------------------------------
# 2. Docker 基础设施（Postgres + Redis，独立容器避免 compose 撞名）
# ----------------------------------------------------------
echo ""
echo "[2/6] 检查 Docker..."
if ! docker info >/dev/null 2>&1; then
    echo "  🔄 Docker 未运行，正在启动 Docker Desktop..."
    open -a Docker
    for _ in {1..30}; do docker info >/dev/null 2>&1 && break; sleep 2; done
fi
docker info >/dev/null 2>&1 || { echo "  ❌ Docker 未就绪，请手动启动后重试"; exit 1; }
echo "  ✅ Docker 就绪"

ensure_container() {
    local name="$1"; shift
    if docker ps --filter "name=$name" --filter status=running --format '{{.Names}}' | grep -q "^$name$"; then
        echo "  ✅ $name 已运行"
    elif docker ps -a --filter "name=$name" --format '{{.Names}}' | grep -q "^$name$"; then
        docker start "$name" >/dev/null && echo "  ✅ $name 已启动"
    else
        "$@" >/dev/null && echo "  ✅ $name 已创建并启动"
    fi
}

echo ""
echo "[3/6] 启动 PostgreSQL / Redis..."
PG_VOLUME="2026-07-06-11-35-36_postgres_data"
if ! docker volume ls -q | grep -q "^$PG_VOLUME$"; then
    # 新环境无历史数据卷：用新卷并补扩展
    PG_VOLUME="agentone_postgres_data"
fi
ensure_container agentone-postgres docker run -d --name agentone-postgres \
    -p "$POSTGRES_PORT":5432 \
    -e POSTGRES_DB=agentone -e POSTGRES_USER=agentone -e POSTGRES_PASSWORD=agentone123 \
    -v "$PG_VOLUME":/var/lib/postgresql/data \
    --restart unless-stopped pgvector/pgvector:pg16
ensure_container agentone-redis docker run -d --name agentone-redis \
    -p "$REDIS_PORT":6379 --restart unless-stopped \
    redis:7-alpine redis-server --requirepass agentone123 --appendonly yes

for _ in {1..20}; do
    docker exec agentone-postgres pg_isready -U agentone >/dev/null 2>&1 && break
    sleep 1
done
docker exec agentone-postgres pg_isready -U agentone >/dev/null 2>&1 || { echo "  ❌ PostgreSQL 未就绪"; exit 1; }
if [ "$PG_VOLUME" = "agentone_postgres_data" ]; then
    docker exec agentone-postgres psql -U agentone -d agentone -c \
        'CREATE EXTENSION IF NOT EXISTS vector; CREATE EXTENSION IF NOT EXISTS "uuid-ossp";' >/dev/null 2>&1 || true
fi
echo "  ✅ PostgreSQL 就绪 (卷: $PG_VOLUME)"

# ----------------------------------------------------------
# 4. 编译（按需）
# ----------------------------------------------------------
JAR=agentone-api/target/agentone-api-1.0.0-SNAPSHOT.jar
echo ""
echo "[4/6] 检查编译..."
if [ "$1" = "--build" ]; then
    echo "  🔄 强制编译..."
    mvn clean package -DskipTests -q
    echo "  ✅ 编译完成"
elif [ ! -f "$JAR" ]; then
    echo "  🔄 jar 不存在，执行首次编译..."
    mvn clean package -DskipTests -q
    echo "  ✅ 编译完成"
elif [ -n "$(find . -name '*.java' -newer "$JAR" -print -quit 2>/dev/null)" ]; then
    echo "  🔄 源码比 jar 新，重新编译..."
    mvn clean package -DskipTests -q
    echo "  ✅ 编译完成"
else
    echo "  ✅ jar 是最新的，跳过编译（--build 可强制）"
fi

# ----------------------------------------------------------
# 5. 后端
# ----------------------------------------------------------
echo ""
echo "[5/6] 启动 Spring Boot..."
# 本机系统 SOCKS 代理会劫持 JVM 本地连接，必须对本地地址绕过
nohup java \
    -DsocksNonProxyHosts="localhost|127.*|192.168.*|10.*|*.local" \
    -Dhttp.nonProxyHosts="localhost|127.*|192.168.*|10.*|*.local" \
    -jar "$JAR" \
    --server.port="$API_PORT" \
    --spring.datasource.url="jdbc:postgresql://127.0.0.1:$POSTGRES_PORT/agentone?stringtype=unspecified" \
    --spring.data.redis.host=127.0.0.1 \
    --spring.data.redis.port="$REDIS_PORT" \
    > /tmp/agentone-backend.log 2>&1 &
APP_PID=$!

for _ in {1..45}; do
    if curl -s "http://127.0.0.1:$API_PORT/v1/health" 2>/dev/null | grep -q '"status":"UP"'; then
        echo "  ✅ 后端就绪 (PID: $APP_PID)"
        break
    fi
    sleep 4
done
curl -s "http://127.0.0.1:$API_PORT/v1/health" 2>/dev/null | grep -q '"status":"UP"' || {
    echo "  ❌ 后端启动超时，查看日志: /tmp/agentone-backend.log"
    exit 1
}

# ----------------------------------------------------------
# 6. 前端
# ----------------------------------------------------------
echo ""
echo "[6/6] 启动前端..."
cd "$SCRIPT_DIR/../agentone-web"
nohup npm run dev > /tmp/agentone-web.log 2>&1 &
WEB_PID=$!
for _ in {1..15}; do
    [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$WEB_PORT" 2>/dev/null)" = "200" ] && break
    sleep 2
done
echo "  ✅ 前端已启动 (PID: $WEB_PID)"

echo ""
echo "=========================================="
echo "  ✅ AgentOne 启动成功！"
echo "=========================================="
echo "  🌐 前端:  http://localhost:$WEB_PORT"
echo "  🏥 后端:  http://localhost:$API_PORT/v1/health"
echo "  📄 日志:  /tmp/agentone-backend.log / /tmp/agentone-web.log"
echo "  停止: kill $APP_PID $WEB_PID"
