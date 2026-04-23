# 맞춰봄 (Matuabom)

> **"서로의 일정을 맞춰보며, 우리가 함께할 시간을 찾아요"**
>
> 맞춰봄은 Google Calendar 연동 기반의 모임 일정 조율 서비스입니다.
> 친구들과 캘린더를 공유하고, 최적의 미팅 시간을 추천해드립니다.

<br/>

## 목차

- [핵심 기능](#핵심-기능)
- [기술 스택](#기술-스택)
- [시스템 아키텍처](#시스템-아키텍처)
- [프로젝트 구조](#프로젝트-구조)
- [로컬 실행 가이드](#로컬-실행-가이드)
- [CI/CD](#cicd)
- [협업 컨벤션](#협업-컨벤션)

---

## 핵심 기능

### 1. Google Calendar 실시간 연동
- OAuth 2.0 기반 Google 계정 연동으로 기존 캘린더 일정을 자동으로 가져옵니다.
- Google Calendar Webhook을 통해 일정 변경사항을 실시간으로 반영합니다.
- 일정 생성 시 Google Calendar에 자동으로 이벤트가 추가됩니다.

### 2. 일정 추천
- 참여자들의 가용 시간대 및 개인 일정을 분석하여 최적의 미팅 슬롯을 추천합니다.

### 3. 미팅 생성 및 참여
- 초대 코드를 통해 간편하게 미팅에 참여할 수 있습니다.
- 참여자별 가능한 시간대를 입력하면 겹치는 시간대를 자동으로 계산합니다.
- SSE(Server-Sent Events) 기반 실시간 업데이트로 즉각적인 응답을 제공합니다.

### 4. 친구 및 통계
- 초대 코드로 친구를 추가하고 함께 일정을 관리할 수 있습니다.
- 대시보드에서 미팅 현황, 자주 만나는 파트너, 시간대별 통계를 확인할 수 있습니다.

---

## 기술 스택

### Backend
| 분류 | 기술 |
|------|------|
| Language & Framework | Java 21, Spring Boot 3.5 |
| Database | PostgreSQL (JPA), MongoDB, Redis |
| Security | Spring Security, JWT, OAuth 2.0 (Kakao, Google) |
| External API | Google Calendar API v3, Google Gemini API |
| Async / Realtime | WebFlux (WebClient), SSE |
| Documentation | SpringDoc OpenAPI (Swagger) |
| Monitoring | Micrometer, Prometheus, Logstash |
| Utilities | Bucket4j (Rate Limiting), Lombok |

### Frontend
| 분류 | 기술 |
|------|------|
| Framework | Next.js 16, React 19 |
| Language | TypeScript 5 |
| Styling | Tailwind CSS 4, shadcn/ui, Radix UI |
| Calendar | FullCalendar |
| Form | React Hook Form, Zod |
| Date | date-fns, date-fns-tz |

### Infrastructure & DevOps
| 분류 | 기술 |
|------|------|
| Cloud | AWS (EC2, RDS, ECR) |
| Container | Docker, Docker Compose |
| Web Server | Nginx (Reverse Proxy, TLS) |
| CI/CD | GitHub Actions |
| Registry | Amazon ECR |
| Monitoring | Prometheus, Grafana, Logstash |
| Load Testing | k6 |

---

## 시스템 아키텍처

```
Client (Browser)
      │
      ▼
   Nginx (80/443, TLS)
      │
      ├──▶ /api/*  ──▶  BE (Spring Boot :8080)
      │                    ├── PostgreSQL (RDS)
      │                    ├── MongoDB
      │                    ├── Redis
      │                    └── AI Service (:5000)
      │
      └──▶ /*      ──▶  FE (Next.js :3000)

CI/CD:
  GitHub → GitHub Actions → Build & Push to ECR → SSH Deploy to EC2
```

- **BE**: Spring Boot Actuator 헬스체크 후 컨테이너 재기동 (`docker compose up --no-deps --force-recreate`)
- **FE**: Next.js 컨테이너 독립 배포, BE에 의존

---

## 프로젝트 구조

```
matuabom/
├── BE/                          # Spring Boot 백엔드
│   └── src/main/java/org/dallyeo/matuabom/
│       ├── admin/               # 관리자
│       ├── ai/                  # AI 일정 추천(현재 사용 안 함)
│       ├── auth/                # 인증 (Kakao/Google OAuth, JWT)
│       ├── calendar/            # Google Calendar 연동/Webhook
│       ├── meeting/             # 미팅 생성/조율
│       ├── sse/                 # 실시간 이벤트 (SSE)
│       ├── stats/               # 통계/대시보드
│       ├── timetable/           # 시간표 관리
│       ├── user/                # 유저/친구
│       └── global/              # 공통 설정, 예외처리, 필터
│
├── FE/                          # Next.js 프론트엔드
│   └── app/
│       ├── home/                # 홈 대시보드
│       ├── meetings/            # 미팅 목록/생성/상세
│       ├── friends/             # 친구 관리
│       ├── ai/                  # AI 추천(현재 사용 안 함)
│       ├── profile/             # 프로필
│       ├── settings/            # 설정
│       └── admin/               # 관리자
│
└── Deploy/                      # 인프라 설정
    ├── docker-compose.yml       # 프로덕션
    ├── docker-compose.local.yml # 로컬
    ├── nginx/                   # Nginx 설정
    ├── prometheus/              # Prometheus 설정
    ├── grafana/                 # Grafana 대시보드
    ├── logstash/                # 로그 수집
    └── k6/                      # 부하 테스트
```

---

## 로컬 실행 가이드

### 사전 요구사항
- Docker & Docker Compose
- Java 21 (BE 단독 실행 시)
- Node.js 20+ (FE 단독 실행 시)

### Docker Compose로 전체 실행

```bash
# 1. Deploy 레포 클론 후 .env 작성
cd Deploy
cp .env.example .env  # 필요한 환경 변수 입력

# 2. 로컬 실행
docker compose -f docker-compose.local.yml up -d
```

### BE 단독 실행

```bash
cd BE
cp .env .env.local   # 환경변수 설정
./gradlew bootRun
# http://localhost:8080/swagger-ui/index.html
```

### FE 단독 실행

```bash
cd FE
cp .env.example .env.local  # NEXT_PUBLIC_API_BASE 설정
npm install
npm run dev
# http://localhost:3000
```

### 필수 환경 변수

| 변수 | 설명 |
|------|------|
| `POSTGRES_URL` | PostgreSQL 연결 URL |
| `REDIS_PASSWORD` | Redis 비밀번호 |
| `JWT_SECRET` | JWT 서명 키 |
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` | 카카오 OAuth |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth & Calendar |
| `GEMINI_API_KEY` | Google Gemini AI |
| `ENCRYPT_KEY` | OAuth 토큰 AES 암호화 키 |
| `GOOGLE_WEBHOOK_TOKEN` | Google Calendar Webhook 검증 토큰 |

---

## CI/CD

### 파이프라인 흐름

```
Push to main/develop
       │
       ▼
GitHub Actions
       │
       ├─ JDK 21 세팅 & Gradle 캐시
       ├─ Docker 이미지 빌드
       ├─ Amazon ECR 푸시 (SHA 태그 + latest)
       ├─ ECR 이미지 최대 3개 유지 (오래된 이미지 자동 삭제)
       │
       └─ EC2 SSH 배포
              ├─ docker compose pull
              ├─ docker compose up --no-deps --force-recreate
              └─ Actuator 헬스체크 (최대 30회 × 5초)
```

- `main`, `develop` 브랜치 push 또는 수동 트리거(`workflow_dispatch`)로 실행됩니다.
- 헬스체크 실패 시 최근 50줄 로그를 출력하고 파이프라인이 실패로 종료됩니다.
- 배포 완료 후 dangling 이미지를 자동으로 정리합니다(`docker image prune -f`).

---

## 협업 컨벤션

### Branch 전략

| 브랜치 | 용도 |
|--------|------|
| `main` | 프로덕션 배포 |
| `develop` | 개발 통합 브랜치 |
| `feat/#<issue>/<name>` | 기능 개발 |
| `fix/#<issue>/<name>` | 버그 수정 |
| `refactor/#<issue>/<name>` | 리팩토링 |
| `chore/#<issue>/<name>` | 설정, 의존성 등 |

모든 작업 브랜치는 `develop`으로 PR 후 머지합니다.

### Commit 메시지

```
<type>: <작업 내용>

예) feat: 미팅 초대 코드 생성 API 추가
    fix: Google Calendar 동기화 누락 버그 수정
    refactor: MeetingService 가용 시간 계산 로직 분리
```

| Type | 설명 |
|------|------|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 |
| `docs` | 문서 수정 |
| `style` | 포맷/스타일 (동작 무관) |
| `test` | 테스트 |
| `chore` | 빌드, 설정 등 |
