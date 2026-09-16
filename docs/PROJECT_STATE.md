# Sports Session & Player Management Platform — Project State

## 1. Snapshot

| Thuộc tính | Giá trị |
| --- | --- |
| Branch | `feature/host-live-session-ui-v1` |
| HEAD | `cc499acaf6838675101782bbf268f723960b2554` |
| Ngày audit | 2026-09-16 |
| Backend | Java 25, Spring Boot 3.5.16, Maven, JPA, Bean Validation, Flyway 12.8.1 |
| Frontend | React 19, TypeScript 6, Vite 8, React Router 7, TanStack Query 5 |
| Database | PostgreSQL 18.4; Hibernate `ddl-auto=validate` |
| Migration head | V9 — Global Player Code |

Đây là living document canonical về trạng thái sản phẩm. Khi tài liệu và source khác nhau, ưu tiên `CODE → TEST → CONFIG → MIGRATION → CURRENT UI → docs/comments/assumptions` và cập nhật lại tài liệu.

## 2. Product Goal

Cung cấp một luồng vận hành phiên cầu lông đôi hoàn chỉnh: Host chuẩn bị dữ liệu, mở và điều hành Session, quản lý người/court, tạo hoặc nhận gợi ý trận, vận hành hàng đợi, giải quyết trận và kết thúc Session. Player có trang cá nhân chỉ đọc để theo dõi trạng thái hiện tại.

## 3. Architecture

### Backend

- Maven single-module, package-based modular monolith dưới base package `com.sportssession.platform`.
- Các module chính: `player`, `venue`, `session`, `match`, `matchplan`, `matchmaking`, `rating`, `shared`.
- Mỗi feature tách lớp `api`, `application`, `domain`, `infrastructure` khi phù hợp.
- Spring MVC REST, Bean Validation, transaction tại application service; domain giữ business invariant.
- Lỗi API được chuẩn hóa tập trung qua `shared.api.GlobalExceptionHandler`.

### Frontend

- React SPA với TypeScript, React Router và TanStack Query.
- Feature packages: `session-setup`, `live-session`, `check-in`, `player-session`, `player-management`.
- API client và DTO contract nằm trong `frontend/src/api`.
- Runtime query được polling; mutation invalidate/refetch dữ liệu có thẩm quyền từ backend, không tự suy diễn business state.
- Vite proxy `/api` đến `http://localhost:8080` trong môi trường development.

### Database

- PostgreSQL là nguồn dữ liệu bền vững; JPA mapping được kiểm tra với `ddl-auto=validate`.
- Flyway quản lý schema. V1–V9 đã áp dụng và bất biến.
- UUID là khóa định danh chính của các aggregate/runtime record; enum domain được lưu dưới dạng giá trị chuỗi theo schema hiện tại.

### Runtime Data Flow

`React UI → /api qua Vite proxy → Controller → Application Service → Domain → Repository/JPA/JDBC → PostgreSQL`

TanStack Query giữ server state phía client. Session, Participants, Session Courts, Matches và MatchPlans được làm mới mỗi 5 giây; dữ liệu tham chiếu Player/Venue/physical Court không polling liên tục. Mutation dùng `retry: false`, sau thành công sẽ invalidate/refetch các query liên quan.

## 4. Domain Model

### Session

State machine đã xác thực:

`PLANNED → IN_PROGRESS → COMPLETED`

`PLANNED | IN_PROGRESS → CANCELLED`

Session chứa cấu hình venue, sport, match format, thời gian dự kiến và lifecycle timestamps. Luồng hiện tại chỉ hỗ trợ `BADMINTON` + `DOUBLES`.

### SessionParticipant

Các trạng thái hiện tại:

- `REGISTERED`: đã được thêm, chưa check-in.
- `WAITING`: đã check-in hoặc được release/resume, sẵn sàng ghép trận.
- `PLAYING`: đang tham gia Match.
- `PAUSED`: tạm ngừng tham gia matchmaking.
- `LEFT`: đã rời Session.

`SessionParticipant UUID` là identity nghiệp vụ. `participantCode`, Buddy Pair và personal access token là các thuộc tính/quan hệ bổ sung, không thay thế identity này.

### Match

State machine:

`CREATED → PLAYING → COMPLETED`

`CREATED | PLAYING → CANCELLED`

Match `CREATED` chưa chiếm Court/Player; việc Start mới chuyển tài nguyên sang `PLAYING`. Complete/Cancel giải phóng trạng thái runtime theo invariant hiện tại.

### MatchPlan

State machine:

`QUEUED → STARTED | CANCELLED`

MatchPlan là kế hoạch/hàng đợi theo Session Court, hỗ trợ tạo, sửa đội hình, di chuyển court, đổi thứ tự, cancel và start. MatchPlan không phải một Match đã chơi. Manual create/update bắt buộc hai thành viên của cùng Buddy Pair phải cùng team nếu cả hai được chọn; chỉ chọn một thành viên vẫn hợp lệ theo semantics hiện tại.

### Buddy Pair

- Là quan hệ giữa đúng hai SessionParticipant trong cùng Session.
- Matchmaking phải chọn đủ cả cặp và xếp họ cùng đội khi cặp còn eligible/được chọn.
- Thay đổi Buddy làm recommendation cũ không còn khớp authoritative evidence và bị từ chối như stale.
- Match/MatchPlan đã tồn tại không bị viết lại hồi tố khi Buddy thay đổi.

### Player / Rating

- Player là identity lâu dài; UUID vẫn là canonical identity, còn `playerCode`
  là mã global immutable do hệ thống sinh để Host hiển thị và tìm kiếm.
- Sports profile hiện có `BADMINTON` và `SkillLevel`.
- `SkillLevel` prior ban đầu: `WEAK=15`, `WEAK_PLUS=19`, `INTERMEDIATE_MINUS=23`, `INTERMEDIATE=27`, `INTERMEDIATE_PLUS=31`, `GOOD=35`; uncertainty ban đầu là `25/3`.
- Rating hiện tại được persist theo Player/Sport/MatchFormat; rating history được ghi thành event theo kết quả Match.

## 5. Identity Model

### Player ID

UUID là canonical identity của Player xuyên suốt hệ thống. API mutation,
navigation và persistence relationship tiếp tục dùng UUID.

### playerCode

Mã global, immutable và human-readable của Player, ví dụ `P000123`:

- PostgreSQL lưu số sequence dạng `BIGINT`; API/UI format `P%06d`;
- globally unique, system-generated và không do client cung cấp;
- chỉ dùng cho display/search, không thay thế UUID làm relational/business identity;
- độc lập với `participantCode` cục bộ của từng Session.

### SessionParticipant UUID

Identity nội bộ/nghiệp vụ canonical của một người trong một Session. Được dùng cho:

- check-in và People operations;
- Buddy Pair;
- Match và MatchPlan;
- quan hệ/candidate matchmaking;
- mọi mutation theo participant.

### participantCode

Mã hiển thị dễ đọc, cục bộ trong Session, ví dụ `#3`, `#12`. Chỉ dùng để hiển thị, Host search và phân biệt tên trùng; không bao giờ dùng làm business identity.

### personalAccessToken

Opaque external locator cho Player Session View:

- UUID ngẫu nhiên, được persist;
- immutable và globally unique;
- không phải identity chung của participant;
- chỉ dùng để resolve đường dẫn `/player-session/:token`.

## 6. Current End-to-End Host Flow

Luồng tạo mới qua UI:

`Home → Create New Session → create/select Venue → create/select Courts → create/select Players → create Session → allocate Courts → add Participants → Start Session → Control Room → Check-In Desk / People Check-In → REGISTERED → WAITING → Buddy / People operations → Runtime Add Court bằng cách chọn existing Court hoặc tạo physical Court rồi allocate → Tạo đề xuất ghép trận hoặc Xếp vào hàng chờ → MatchPlan Queue → Start Match → Complete / Cancel Match → Complete / Cancel Session`

Luồng placement canonical của Host:

```text
WAITING
├─ Tạo đề xuất ghép trận
└─ Xếp vào hàng chờ
        ↓
MatchPlan Queue
        ↓
Court
        ↓
Match
```

Manual Match backend capability và lifecycle của Match đã tồn tại vẫn được giữ
cho recovery, API compatibility và future admin, nhưng tạo Manual Match trực
tiếp không còn là primary Host UI path.

Player trùng `displayName` được phân biệt trước khi cấp phát vào Session bằng
`playerCode · displayName`; mọi lựa chọn/mutation vẫn gửi Player UUID.

Luồng discovery/resume, gồm recovery cho Session chưa bắt đầu:

`Home → discover PLANNED Session → open Session bằng Session UUID → Start → IN_PROGRESS → Control Room`

**A Host can operate a newly created Session end-to-end using UI only.** Normal path không cần Swagger.

## 7. Current End-to-End Player Flow

`Host lấy personal link/QR → /player-session/:token → resolve Session + SessionParticipant UUID → Player Session View chỉ đọc`

Player View hiển thị runtime state `REGISTERED`, `WAITING`, `QUEUED`, `PLAYING`, `PAUSED`, `LEFT`; khi phù hợp còn hiển thị Court, teammate và opponents.

- Player View là read-only.
- Player View tiếp tục polling và có nút `Làm mới` để chủ động refetch dữ liệu
  authoritative mà không reload trình duyệt.
- Host thực hiện check-in.
- Player self check-in: **NOT IMPLEMENTED**, không phải current requirement.
- QR auto check-in: **NOT IMPLEMENTED**, không phải current requirement.
- QR chỉ mở personal page, không tạo mutation.

## 8. Feature Status Matrix

| Capability | Backend | Frontend | Overall | Notes |
| --- | --- | --- | --- | --- |
| Create Player | DONE | DONE | DONE | Có quản lý danh sách/detail và SkillLevel |
| Global Player Code V1 | DONE | DONE | DONE | Global display/search code; UUID vẫn canonical |
| Session-local completed Match count visibility | N/A | DONE | DONE | People rows hiển thị count từ authoritative Match collection |
| Player Session View manual refresh | N/A | DONE | DONE | Bổ sung refetch chủ động, giữ polling/read-only |
| Recommendation refresh wording clarification | N/A | DONE | DONE | Recompute dùng “Cập nhật đề xuất”, không hứa phương án khác |
| Create Venue | DONE | DONE | DONE | Có trong Session Setup |
| Create Court | DONE | DONE | DONE | Tạo physical Court trong Setup hoặc Control Room |
| Create Session | DONE | DONE | DONE | Setup UI không cần Swagger |
| Session Discovery / Resume | DONE | DONE | DONE | Home list và mở lại bằng Session UUID |
| PLANNED Session Recovery Start V1 | DONE | DONE | DONE | Start tại Session page bằng API lifecycle hiện có |
| Add Participant | DONE | DONE | DONE | Allocate lúc Setup |
| Add Court | DONE | DONE | DONE | Allocate lúc Setup |
| Start Session | DONE | DONE | DONE | Normal path trong Setup |
| Complete Session | DONE | DONE | DONE | Control Room có confirmation |
| Cancel Session | DONE | DONE | DONE | Control Room có confirmation |
| Runtime Add Participant | DONE | DONE | DONE | Thêm existing eligible Player |
| Runtime Add Court | DONE | DONE | DONE | Chọn existing Court hoặc tạo physical Court rồi allocate |
| Runtime Create Physical Court V1 | DONE | DONE | DONE | Hai API tuần tự, có recovery khi allocation thất bại |
| Check-In | DONE | DONE | DONE | Host desk và People action |
| Buddy Pair | DONE | DONE | DONE | Create/remove, có matchmaking invariant |
| Manual Match | DONE | DONE | DONE | Backend create vẫn tồn tại; Host UI giữ start/cancel/complete recovery cho Match hiện có |
| Matchmaking | DONE | DONE | DONE | Generate, dismiss và queue; accept/start API vẫn tồn tại nhưng không còn là primary Host action |
| MatchPlan Queue | DONE | DONE | DONE | Per-court queue và điều phối plan |
| Host Match Placement Simplification | N/A | DONE | DONE | Hai entry point chính hội tụ vào MatchPlan Queue |
| Manual MatchPlan Buddy Consistency | DONE | DONE | DONE | Server reject split Buddy; editor hiển thị Buddy và chặn submit sai team |
| Complete/Cancel Match | DONE | DONE | DONE | Manual và recommendation Match |
| Personal Link | DONE | DONE | DONE | Opaque token được cấp theo participant |
| QR | DONE | DONE | DONE | QR mở personal read-only page |
| Player Session View | DONE | DONE | DONE | Hỗ trợ toàn bộ participant runtime states |
| Host Vietnamese UI | N/A | DONE | DONE | Thuật ngữ hiển thị đã được Việt hóa nhất quán; giữ QR theo cách dùng tự nhiên |

## 9. Host UI Capability Checklist

- [x] Start Session
- [x] Complete Session
- [x] Cancel Session
- [x] Runtime Add Participant
- [x] Runtime Add Court
- [x] Runtime Create Physical Court V1
- [x] Setup without Swagger
- [x] Session Discovery / Resume
- [x] PLANNED Session Recovery Start V1
- [x] Vietnam timezone
- [x] Host Check-In Desk
- [x] Personal Link
- [x] QR Personal Link
- [x] Vietnamese terminology polish
- [x] Rating uncertainty explanation / history presentation
- [x] Session → Home navigation
- [x] Global Player Code V1 cho duplicate-name selection
- [x] Session-local completed Match count visibility
- [x] Player Session View manual refresh
- [x] Recommendation refresh wording clarification
- [x] Host Match Placement Simplification
- [x] Manual MatchPlan Buddy Consistency

## 10. Matchmaking Current State

Algorithm hiện tại: `fairness-anchor-buddy-level-session-count-diversity-rating-sum-v5`.

Thứ tự chọn đã xác thực:

1. Chỉ xét candidate hợp lệ và bắt buộc chứa participant chờ lâu nhất (oldest anchor).
2. Buddy Pair phải được chọn đủ và nằm cùng team.
3. Tối thiểu hóa `SkillLevel` spread.
4. Tối ưu vector số Match đã chơi trong Session.
5. Tránh lặp ngay quartet gần nhất.
6. Tối thiểu teammate repeat count.
7. Tối thiểu opponent repeat count.
8. Tối thiểu chênh lệch tổng Rating hai team.
9. Waiting vector tie-break.
10. Player UUID và partition key làm deterministic tie-break.

Recommendation là preview, không tự chiếm resource. Queue/Accept backend tái tạo và so sánh authoritative evidence; thay đổi participant, Court, Buddy, algorithm version hoặc composition khiến evidence cũ bị từ chối như stale. Trong Host UI bình thường, recommendation chỉ được đưa vào MatchPlan Queue; accept-and-start backend vẫn tồn tại cho API compatibility nhưng không còn được expose như primary action.

People rows hiển thị số Match `COMPLETED` trong Session theo
`SessionParticipant UUID`, cùng semantics với fairness reader của backend.
Recommendation là deterministic với cùng authoritative state; UI dùng “Cập
nhật đề xuất” và giải thích kết quả có thể không đổi.

## 11. Rating Current State

- Model: Weng-Lin Plackett-Luce, algorithm `weng-lin-pl-v1`, khóa ở `BADMINTON` + `DOUBLES`.
- Prior Rating được khởi tạo từ `SkillLevel`; Rating và uncertainty được persist trong `player_ratings`.
- Match `COMPLETED` result version 1 là evidence; mỗi lần áp dụng tạo `rating_events`, có idempotency/integrity check.
- Reconciliation tìm completed Match chưa xử lý theo chronology, dùng PostgreSQL advisory lock theo rating context và xử lý atomic.
- Scheduler reconciliation bật mặc định, fixed delay 5 giây; test profile tắt scheduler. Vì vậy cập nhật Rating là asynchronous đối với lifecycle hoàn tất Match.
- Player API trả current Rating và lịch sử Rating; frontend Player Management hiển thị các thông tin này.

## 12. API Capability Map

| Nhóm | API hiện có |
| --- | --- |
| Player | Create, list/search theo name/code, get detail, update SkillLevel, read Rating history |
| Venue/Court | Create/list/get Venue; create/list Court theo Venue; get Court |
| Session | Create/list/get; start/complete/cancel; add/list Participants; check-in/pause/resume/leave; add/list/enable/disable Session Courts |
| Buddy/Personal access | Create/remove Buddy Pair; get participant personal access; resolve personal token |
| Match | List theo Session; create manual; start/complete/cancel |
| MatchPlan | Create/list/update/move/reorder/cancel/start |
| Matchmaking | Generate theo Court; accept/start; queue; session-wide preview và queue batch |

`GET /api/sessions` trả collection phẳng, không load Participants/Courts/Matches. Backend sắp xếp `IN_PROGRESS → PLANNED → terminal`, rồi `plannedStartAt DESC`, `id ASC`. Pagination được hoãn cho đến khi volume Session thực tế yêu cầu.

## 13. Frontend Page / Component Map

| Route / khu vực | Trách nhiệm |
| --- | --- |
| `/` | Home; tạo Session mới, xem/mở lại Session hiện có hoặc vào Player Management |
| `/sessions/new` | Full Session Setup: master data, Session, allocations và Start |
| `/sessions/:sessionId` | Host Live Control Room: People, Buddy, Courts, Matches, Matchmaking, Queue, lifecycle |
| `/sessions/:sessionId/check-in` | Host Check-In Desk, tìm kiếm/mã participant và personal link |
| `/player-session/:token` | Resolve opaque token và mở Player View |
| `/sessions/:sessionId/player/:sessionParticipantId` | Player Session View nội bộ, read-only |
| `/players` | Player list/search/create |
| `/players/:playerId` | Player detail, SkillLevel, current Rating/history |

Các component/hook chính được tổ chức trong `session-setup`, `live-session`, `check-in`, `player-session` và `player-management`; API calls không đặt trực tiếp vào page component.

## 14. Database Migrations

| Version | Nội dung |
| --- | --- |
| V1 | Player / Venue foundation |
| V2 | Session runtime |
| V3 | Match runtime |
| V4 | Rating runtime |
| V5 | MatchPlan Queue |
| V6 | Session Buddy Pair |
| V7 | Session Participant Code |
| V8 | Participant Personal Access Token |
| V9 | Global Player Code |

**V1–V9 IMMUTABLE.** Mọi thay đổi schema tương lai phải dùng migration version mới; không sửa migration đã áp dụng.

## 15. Testing / Verification

- Backend: JUnit/Spring integration tests với Testcontainers PostgreSQL 18.4; có Flyway schema/invariant coverage và pure-domain tests.
- Frontend: Vitest, Testing Library, jsdom; có API contract, model/hook và component interaction coverage; checkpoint còn kiểm tra lint, TypeScript/Vite build và `git diff --check`.
- Focused backend sau Manual MatchPlan Buddy Consistency: **22 tests PASS**, PostgreSQL 18.4.
- Full backend sau Manual MatchPlan Buddy Consistency: **641 tests PASS**, 0 failures/errors/skips, PostgreSQL 18.4.
- Focused frontend sau Manual MatchPlan Buddy Consistency: **20 tests PASS**.
- Full frontend sau Manual MatchPlan Buddy Consistency: **377 tests PASS**; lint và production build PASS.

## 16. Current Work

**Current Work: None — continue MVP manual acceptance**

## 17. Next Recommended Work

1. **Tiếp tục MVP Manual Browser Acceptance Test**
2. **MVP Checkpoint / Tag / Release Preparation**

## 18. Deferred / Not Now

- `SINGLES`.
- Player self check-in.
- QR auto check-in.
- Auth redesign.
- Token rotation, expiry hoặc revocation.
- WebSocket/realtime infrastructure, trừ khi requirement tương lai chứng minh polling hiện tại không đủ.

## 19. Engineering Invariants

- V1 hiện tại chỉ hỗ trợ `BADMINTON` + `DOUBLES`; `SINGLES` được hoãn.
- Backend `Instant`/UTC là thời gian authoritative; frontend trình bày bằng `Asia/Ho_Chi_Minh`.
- Backend business state là authoritative; không optimistic business-state transition.
- Production mutation dùng `retry: false`.
- `SessionParticipant UUID` là identity thật cho business operation.
- Player UUID là canonical identity; `playerCode` chỉ dùng display/search.
- `participantCode` chỉ dùng hiển thị/search, không làm khóa nghiệp vụ.
- `personalAccessToken` chỉ là locator cho Player View, không phải participant identity.
- V1–V9 đã áp dụng là immutable.
- Live status thuộc `SessionCourt`, không thuộc physical `Court`.
- Buddy Pair không được tách khi pair eligible/được chọn.
- Manual MatchPlan create/update phải xếp hai Buddy được chọn vào cùng team; một Buddy member được chọn riêng vẫn hợp lệ.
- Buddy change không viết lại hồi tố Match/MatchPlan hiện có.
- Recommendation phải được kiểm tra stale với authoritative state trước Accept/Queue.
- QR chỉ mở Player View; không thực hiện check-in mutation.
- Host kiểm soát check-in.

## 20. Obsolete Backlog

Các item sau đã được source/test/UI xác nhận **DONE** và không còn là current backlog:

- Session Start / Complete / Cancel UI.
- Runtime Add Participant / Runtime Add Court.
- Full Session Setup UI không cần Swagger.
- Vietnam timezone handling.
- Buddy Pair backend và Host UI.
- Session Participant Code backend và Host UI.
- Manual Match runtime và frontend lifecycle.
- Matchmaking recommendation, stale protection và Queue; accept/start backend vẫn được giữ.
- MatchPlan Queue và Court Queue UI.
- Host Match Placement Simplification với hai primary entry point hội tụ vào Queue.
- Manual MatchPlan Buddy consistency ở backend và Host queue editor.
- Player Session View.
- Personal random token, Host personal link và QR personal link.
- Host Check-In Desk và Host manual check-in.
- Session Discovery / Resume V1 với deterministic backend ordering.
- PLANNED Session Recovery Start V1 tại Session page.
- Runtime Create Physical Court V1 với partial-failure recovery.
- Global Player Code V1 với duplicate-name-safe selectors.
- Session-local completed Match count visibility.
- Player Session View manual refresh.
- Recommendation refresh wording clarification.

Không chuyển các item này trở lại `Current Work` nếu chưa có regression hoặc requirement mới được source chứng minh.

## 21. Known Gaps / Caveats

- Manual browser acceptance là bước verification, không phải missing feature.
- Host Match Placement Simplification đã hoàn tất; tiếp tục manual browser acceptance trước release checkpoint.
- Player self check-in và QR auto check-in không được triển khai và không phải current requirement.

## 22. How To Use This Document

Trước mỗi feature mới:

1. Đọc tài liệu này.
2. Audit source liên quan.
3. Xác nhận feature chưa `DONE`.
4. Cập nhật `Current Work`.
5. Implement một coherent slice.
6. Chạy focused verification.
7. Chỉ chạy full checkpoint khi phù hợp.
8. Cập nhật Feature Status.
9. Cập nhật Remaining Gaps / Next Recommended Work.
10. Commit/push feature cùng project-state update khi phù hợp.
