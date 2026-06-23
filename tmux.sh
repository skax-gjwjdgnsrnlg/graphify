#!/bin/bash

# tmux.sh - Claude Code + OMC + Skills 개발 환경 관리 스크립트
# 사용법: ./tmux.sh [start|stop|restart|attach|status|build|kill|help]

SESSION_NAME="claude-dev"
COMPOSE_FILE="docker-compose.yml"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }
log_agent()   { echo -e "${CYAN}[AGENT]${NC} $1"; }

session_exists() {
    tmux has-session -t "${1:-$SESSION_NAME}" 2>/dev/null
}

# ─────────────────────────────────────────────
# 🌟 NEW: 에이전트 Pane 자동 확장/축소 백그라운드 루프
# ─────────────────────────────────────────────
run_pane_resizer() {
    local s=$SESSION_NAME
    local last_pane_count=2

    # 세션이 살아있는 동안 무한 루프 돌며 Pane 개수 감시
    while session_exists "$s"; do
        # Window 0(orchestrator)의 현재 pane 개수 파악
        local current_pane_count=$(tmux list-panes -t "$s:0" 2>/dev/null | wc -l | tr -d ' ')
        
        # 유효한 숫자가 잡혔을 때만 로직 실행
        if [[ "$current_pane_count" =~ ^[0-9]+$ ]]; then
            if [ "$current_pane_count" -gt 2 ] && [ "$last_pane_count" -le 2 ]; then
                # 🤖 에이전트가 생성됨 (Pane이 3개 이상이 됨)
                # -> 메인 입력창(pane 0.0)의 높이를 축소하여 하단/우측 에이전트 pane 화면을 확장
                tmux resize-pane -t "$s:0.0" -y "35%"
                last_pane_count=$current_pane_count
            elif [ "$current_pane_count" -le 2 ] && [ "$last_pane_count" -gt 2 ]; then
                #  에이전트가 전부 종료됨 (기본 상태인 2개로 복귀)
                # -> 메인 입력창(pane 0.0)을 다시 넓게 확장
                tmux resize-pane -t "$s:0.0" -y "70%"
                last_pane_count=$current_pane_count
            fi
        fi
        sleep 0.8 # 0.8초 주기로 가볍게 체크 (CPU 소모 거의 없음)
    done
}

# ─────────────────────────────────────────────
# Window 0: Orchestrator
# ─────────────────────────────────────────────
create_window_orchestrator() {
    local s=$SESSION_NAME

    tmux new-session -d -s "$s" -n "orchestrator"

    # 좌측 세로 분할 (상: OMC claude, 하: 로그)
    tmux split-window -v -t "$s:0"

    # 상단 70% / 하단 30%
    tmux resize-pane -t "$s:0.0" -y "70%"

    # pane 0: OMC 전용 Claude Code
    tmux send-keys -t "$s:0.0" "cd $(pwd)" C-m
    tmux send-keys -t "$s:0.0" "echo '╔══════════════════════════════════════╗'" C-m
    tmux send-keys -t "$s:0.0" "echo '║   OMC Orchestrator — Agent Commander ║'" C-m
    tmux send-keys -t "$s:0.0" "echo '╚══════════════════════════════════════╝'" C-m
    tmux send-keys -t "$s:0.0" "claude" C-m

    # pane 1: Agent Activity Log
    tmux send-keys -t "$s:0.1" "cd $(pwd)" C-m
    tmux send-keys -t "$s:0.1" "echo '[ Agent Activity Log ]'" C-m
    tmux send-keys -t "$s:0.1" "tail -f ~/.claude/logs/*.log 2>/dev/null || echo 'Claude 로그 대기 중...'" C-m

    tmux select-pane -t "$s:0.0"
}

# ─────────────────────────────────────────────
# Window 1: Project Management
# ─────────────────────────────────────────────
create_window_project_mgmt() {
    local s=$SESSION_NAME

    tmux new-window -t "$s:1" -n "project-mgmt"

    # 좌우 분할
    tmux split-window -h -t "$s:1"

    # 우측을 상하 분할 (플랜 뷰어 + claude)
    tmux select-pane -t "$s:1.1"
    tmux split-window -v -t "$s:1.1"

    # 좌 30% / 우 70%
    tmux resize-pane -t "$s:1.0" -x "30%"

    # 우상 40% / 우하 60% (claude가 더 커야 편함)
    tmux resize-pane -t "$s:1.1" -y "40%"

    # pane 0 (좌): 일반 터미널
    tmux send-keys -t "$s:1.0" "cd $(pwd)" C-m
    tmux send-keys -t "$s:1.0" "echo '[ Project Terminal ]'" C-m
    tmux send-keys -t "$s:1.0" "git log --oneline -10 2>/dev/null || echo '(git 없음)'" C-m

    # pane 1 (우상): planning-with-files 실시간 뷰어
    tmux send-keys -t "$s:1.1" "cd $(pwd)" C-m
    tmux send-keys -t "$s:1.1" "echo '[ planning-with-files — task_plan.md / progress.md ]'" C-m
    tmux send-keys -t "$s:1.1" "watch -n 3 'if [ -f task_plan.md ]; then echo \"=== task_plan.md ===\"; cat task_plan.md; echo \"\"; echo \"=== progress.md ===\"; cat progress.md 2>/dev/null; else echo \"task_plan.md 없음 — Claude에게 플랜 요청 시 자동 생성\"; fi'" C-m

    # pane 2 (우하): Claude — 3개 skill 통합 세션
    tmux send-keys -t "$s:1.2" "cd $(pwd)" C-m
    tmux send-keys -t "$s:1.2" "echo '╔══════════════════════════════════════════╗'" C-m
    tmux send-keys -t "$s:1.2" "echo '║  Project Management — Skills 통합 Claude ║'" C-m
    tmux send-keys -t "$s:1.2" "echo '╠══════════════════════════════════════════╣'" C-m
    tmux send-keys -t "$s:1.2" "echo '║  planning-with-files : /plan-status      ║'" C-m
    tmux send-keys -t "$s:1.2" "echo '║  cc-sessions         : mek: / finito     ║'" C-m
    tmux send-keys -t "$s:1.2" "echo '║  claude-historian    : 자연어 이력 검색  ║'" C-m
    tmux send-keys -t "$s:1.2" "echo '╚══════════════════════════════════════════╝'" C-m
    tmux send-keys -t "$s:1.2" "claude" C-m

    tmux select-pane -t "$s:1.0"
}

# ─────────────────────────────────────────────
# Window 2: Docker Monitor
# ─────────────────────────────────────────────
create_window_docker_monitor() {
    local s=$SESSION_NAME
    local compose_file="$(pwd)/docker-compose.yml"

    tmux new-window -t "$s:2" -n "docker-monitor"

    # 좌우 분할
    tmux split-window -h -t "$s:2"

    # 우측을 상하 분할 (로그 + stats)
    tmux select-pane -t "$s:2.1"
    tmux split-window -v -t "$s:2.1"

    # 좌 40% / 우 60%
    tmux resize-pane -t "$s:2.0" -x "40%"

    # 우상 60% / 우하 40%
    tmux resize-pane -t "$s:2.1" -y "60%"

    # pane 0 (좌): 컨테이너 상태 (watch)
    tmux send-keys -t "$s:2.0" "cd $(pwd)" C-m
    tmux send-keys -t "$s:2.0" "echo '[ Docker Container Status ]'" C-m
    if [ -f "$compose_file" ]; then
        tmux send-keys -t "$s:2.0" "watch -n 3 'docker compose -f $compose_file ps --format \"table {{.Name}}\\t{{.Status}}\\t{{.Ports}}\"'" C-m
    else
        tmux send-keys -t "$s:2.0" "watch -n 3 'docker ps --format \"table {{.Names}}\\t{{.Status}}\\t{{.Ports}}\"'" C-m
    fi

    # pane 1 (우상): 로그 스트리밍
    tmux send-keys -t "$s:2.1" "cd $(pwd)" C-m
    tmux send-keys -t "$s:2.1" "echo '[ Docker Compose Logs ]'" C-m
    if [ -f "$compose_file" ]; then
        tmux send-keys -t "$s:2.1" "docker compose -f $compose_file logs -f --tail=50 2>&1 | grep -v '^Attaching'" C-m
    else
        tmux send-keys -t "$s:2.1" "echo 'docker-compose.yml 없음 — docker logs <container> 로 직접 확인'" C-m
    fi

    # pane 2 (우하): docker stats
    tmux send-keys -t "$s:2.2" "cd $(pwd)" C-m
    tmux send-keys -t "$s:2.2" "echo '[ Docker Stats — CPU / Memory ]'" C-m
    tmux send-keys -t "$s:2.2" "docker stats --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}'" C-m

    tmux select-pane -t "$s:2.1"
}

# ─────────────────────────────────────────────
# Window 3: Filesystem
# ─────────────────────────────────────────────
create_window_filesystem() {
    local s=$SESSION_NAME

    tmux new-window -t "$s:3" -n "filesystem"

    tmux split-window -h -t "$s:3"

    # 좌 20% (좁게)
    tmux resize-pane -t "$s:3.0" -x "20%"

    # pane 0 (좌): 터미널
    tmux send-keys -t "$s:3.0" "cd $(pwd)" C-m
    tmux send-keys -t "$s:3.0" "echo '[ Terminal ]'" C-m

    # pane 1 (우): yazi
    tmux send-keys -t "$s:3.1" "cd $(pwd)" C-m
    tmux send-keys -t "$s:3.1" "yazi" C-m

    tmux select-pane -t "$s:3.1"
}

# ─────────────────────────────────────────────
# Docker
# ─────────────────────────────────────────────
start_containers() {
    [ ! -f "$COMPOSE_FILE" ] && { log_warning "docker-compose.yml 없음 — 건너뜀"; return 0; }
    log_info "도커 컨테이너를 시작합니다..."
    docker compose -f $COMPOSE_FILE up -d
    if [ $? -eq 0 ]; then
        log_success "컨테이너 시작 완료"
        sleep 3
    else
        log_error "컨테이너 시작 실패"; exit 1
    fi
}

stop_containers() {
    [ ! -f "$COMPOSE_FILE" ] && return 0
    log_info "도커 컨테이너를 중지합니다..."
    docker compose -f $COMPOSE_FILE down && log_success "컨테이너 중지 완료" || { log_error "중지 실패"; exit 1; }
}

# ─────────────────────────────────────────────
# 환경 제어
# ─────────────────────────────────────────────
start_env() {
    log_info "=== Claude Code + OMC + Skills 환경 시작 ==="

    if session_exists; then
        log_warning "세션 '$SESSION_NAME' 이미 존재 — './tmux.sh attach' 로 연결하세요."
        exit 1
    fi

    start_containers

    log_info "tmux 세션 생성 중..."
    create_window_orchestrator
    create_window_project_mgmt
    create_window_docker_monitor
    create_window_filesystem

    # agent 완료 시 pane 자동 닫힘
    tmux set-option -t $SESSION_NAME remain-on-exit off
    # 닫힌 window 번호 자동 정리
    tmux set-option -t $SESSION_NAME renumber-windows on

    tmux select-window -t $SESSION_NAME:0
    tmux select-pane  -t $SESSION_NAME:0.0

    # 🌟 NEW: 백그라운드에서 Pane 감시 스크립트 실행 (세션이 종료되면 같이 자동 소멸됨)
    run_pane_resizer &

    log_success "=== 환경 준비 완료 ==="
    echo ""
    log_agent "Window 0 (orchestrator)    : OMC claude + 로그 (🤖 에이전트 구동 시 자동 레이아웃 리사이징 활성화)"
    log_info  "Window 1 (project-mgmt)    : 터미널 + plan뷰어 + Skills claude"
    log_info  "Window 2 (docker-monitor)  : 컨테이너 상태 + 로그 + stats"
    log_info  "Window 3 (filesystem)      : 터미널 + yazi"
    echo ""
    sleep 1
    tmux attach -t $SESSION_NAME
}

stop_env() {
    log_info "=== 환경 종료 ==="
    stop_containers
    if session_exists; then
        # 🌟 NEW: 리사이저 백그라운드 프로세스까지 동반 종료하기 위해 세션 kill 실행
        tmux kill-session -t $SESSION_NAME
        log_success "세션 종료 완료"
    else
        log_warning "실행 중인 세션 없음"
    fi
}

restart_env() {
    stop_env; echo ""; sleep 2; start_env
}

attach_session() {
    if session_exists; then
        local t="$SESSION_NAME"; [ -n "$1" ] && t="$SESSION_NAME:$1"
        tmux attach -t "$t"
    else
        log_error "세션 없음 — './tmux.sh start' 먼저 실행하세요."; exit 1
    fi
}

show_status() {
    echo "=== Claude Code + OMC + Skills 환경 상태 ==="
    echo ""
    echo "📺 tmux 세션:"
    if session_exists; then
        echo -e "  ${GREEN}✓${NC} '$SESSION_NAME' 실행 중"
        tmux list-panes -s -t $SESSION_NAME -F "    #W.#P  [#{pane_current_command}]" 2>/dev/null
    else
        echo -e "  ${RED}✗${NC} 세션 없음"
    fi

    echo ""
    echo "📋 Skills 설치 상태:"
    [ -d ~/.claude/skills/planning-with-files ] \
        && echo -e "  ${GREEN}✓${NC} planning-with-files" \
        || echo -e "  ${RED}✗${NC} planning-with-files (미설치: npx skills add OthmanAdi/planning-with-files -g)"

    ( command -v sessions &>/dev/null || npm list -g cc-sessions &>/dev/null 2>&1 ) \
        && echo -e "  ${GREEN}✓${NC} cc-sessions" \
        || echo -e "  ${RED}✗${NC} cc-sessions (미설치: npm install -g cc-sessions)"

    [ -d ~/.claude/skills/claude-historian ] \
        && echo -e "  ${GREEN}✓${NC} claude-historian-mcp" \
        || echo -e "  ${RED}✗${NC} claude-historian-mcp (미설치: npx skills add Vvkmnn/claude-historian-mcp -g)"

    echo ""
    if [ -f "$COMPOSE_FILE" ]; then
        echo "🐳 도커 컨테이너:"
        docker compose -f $COMPOSE_FILE ps 2>/dev/null | tail -n +2 | while read line; do
            echo "$line" | grep -q "Up" \
                && echo -e "  ${GREEN}✓${NC} $line" \
                || echo -e "  ${RED}✗${NC} $line"
        done
    fi
}

kill_session() {
    if session_exists; then
        tmux kill-session -t $SESSION_NAME && log_success "세션 종료"
    else
        log_warning "실행 중인 세션 없음"
    fi
}

build_env() {
    [ ! -f "$COMPOSE_FILE" ] && { log_error "docker-compose.yml 없음"; exit 1; }
    local svc="${1:-}"
    if [ -n "$svc" ]; then
        docker compose -f $COMPOSE_FILE build "$svc" && docker compose -f $COMPOSE_FILE up -d "$svc"
    else
        docker compose -f $COMPOSE_FILE build && docker compose -f $COMPOSE_FILE up -d
    fi
    [ $? -eq 0 ] && log_success "빌드 완료" || { log_error "빌드 실패"; exit 1; }
}

show_help() {
    cat << EOF
${GREEN}Claude Code + OMC + Skills 개발 환경${NC}

${YELLOW}사용법:${NC}
  ./tmux.sh [command]

${YELLOW}명령어:${NC}
  ${GREEN}start${NC}           - 환경 시작
  ${GREEN}stop${NC}            - 환경 종료
  ${GREEN}restart${NC}         - 재시작
  ${GREEN}attach [N]${NC}      - 세션 접속 (N: 윈도우 번호 0~3)
  ${GREEN}status${NC}          - 상태 + Skills 설치 확인
  ${GREEN}build [svc]${NC}     - 도커 빌드 후 재시작
  ${GREEN}kill${NC}            - tmux 세션만 강제 종료
  ${GREEN}help${NC}            - 도움말

${YELLOW}세션 레이아웃 (claude-dev):${NC}

  ${CYAN}Window 0: orchestrator${NC}
  ┌─────────────────────────────────┐
  │  OMC claude  (agent 지휘)       │  ← 여기서 명령
  ├─────────────────────────────────┤
  │  Agent Activity Log (tail)      │  ← 로그 확인
  └─────────────────────────────────┘
  * agent spawn 시 우측에 pane 자동 생성, 완료 시 자동 제거
  * 에이전트 구동 감지 시 메인 pane이 상단 35%로 자동 축소되어 시야 확보!

  ${GREEN}Window 1: project-mgmt${NC}
  ┌──────┬──────────────────────────┐
  │      │  task_plan.md / progress │  ← planning-with-files watch
  │ git  ├──────────────────────────┤
  │ term │  Skills 통합 Claude      │  ← /plan-status, mek:, finito, 이력검색
  └──────┴──────────────────────────┘

  ${CYAN}Window 2: docker-monitor${NC}
  ┌──────────────────┬───────────────────────┐
  │  watch docker ps │  docker compose       │
  │  (컨테이너 상태) │  logs -f (스트리밍)   │
  │                  ├───────────────────────┤
  │                  │  docker stats         │
  │                  │  (CPU / 메모리)        │
  └──────────────────┴───────────────────────┘

  ${BLUE}Window 3: filesystem${NC}
  ┌────┬────────────────────────────┐
  │term│  yazi                      │
  └────┴────────────────────────────┘

${YELLOW}tmux 단축키:${NC}
  Ctrl+b 0~3   - 윈도우 이동
  Ctrl+b arrow - pane 이동
  Ctrl+b z     - pane 전체화면 토글
  Ctrl+b [     - 스크롤 모드 (q 종료)
  Ctrl+b d     - detach
  Ctrl+b x     - pane 닫기
EOF
}

# ─────────────────────────────────────────────
# 메인
# ─────────────────────────────────────────────
case "${1:-help}" in
    start)            start_env ;;
    stop)             stop_env ;;
    restart)          restart_env ;;
    attach)           attach_session "$2" ;;
    status)           show_status ;;
    build)            build_env "$2" ;;
    kill)             kill_session ;;
    help|--help|-h)   show_help ;;
    *)
        log_error "알 수 없는 명령어: $1"
        echo ""
        show_help
        exit 1
        ;;
esac