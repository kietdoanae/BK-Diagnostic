# BK Diagnostic — SQL Scripts

Toàn bộ SQL của project được tổ chức theo mục đích sử dụng. Tất cả script đều **idempotent** (chạy lại nhiều lần an toàn) trừ khi có ghi chú khác.

## Cấu trúc

```
sql/
├── schema/        # DDL: tables, columns, FKs, indexes, RLS policies
├── storage/       # Supabase Storage buckets + policies
├── rpc/           # Stored procedures / RPC functions
├── migrations/    # Time-stamped incremental changes (date-prefixed)
├── seed/          # Dữ liệu mẫu (test accounts, lab content)
└── diagnostic/    # Query debug / kiểm tra trạng thái DB
```

## Thứ tự apply lần đầu (fresh database)

Chạy trên Supabase SQL Editor theo đúng thứ tự sau:

### 1. Schema (DDL)
```
sql/schema/01-lab-phase1-helpers.sql      # is_staff(), user_is_in_group(), code generators
sql/schema/02-lab-schema.sql              # labs, lab_sessions, lab_groups, lab_steps, lab_questions, lab_evidence ...
sql/schema/03-lab-helpers.sql             # các helper bổ sung dùng trong RLS
sql/schema/04-lab-rls.sql                 # Row Level Security policies cho mọi bảng lab
sql/schema/05-lab-profiles-fk.sql         # FK constraints lab_*.user_id → public.profiles(id) cho PostgREST embed
sql/schema/06-activity-logs.sql           # bảng activity_logs + RLS + RPC log_activity / get_activity_logs
sql/schema/07-export-records.sql          # bảng export_records cho CAN raw exports
```

### 2. Storage
```
sql/storage/01-lab-storage.sql            # bucket lab-evidence, lab-reports + policies
sql/storage/02-exports-storage.sql        # bucket exports + policies
```

### 3. RPC
```
sql/rpc/01-lab-rpc.sql                    # generate_session_code, activate_lab_session, push_evidence ...
sql/rpc/02-lab-complete-session-rpc.sql   # complete_lab_session
```

### 4. Migrations (incremental — chạy theo thứ tự ngày)
Áp dụng các migration cho database đã tồn tại. Bỏ qua nếu chạy schema mới hoàn toàn (đã bao gồm các thay đổi này).
```
sql/migrations/2026-04-15-mssv-fields.sql                      # thêm mssv + full_name vào profiles
sql/migrations/2026-04-26-edu-vn-student-role.sql              # role=student tự động cho email .edu.vn
sql/migrations/2026-04-27-postgrest-profile-joins-fix.sql      # FK constraints idempotent (PostgREST embed)
sql/migrations/2026-04-29-profiles-select-policy.sql           # RLS cho phép authenticated SELECT all profiles
```

### 5. Seed (tùy chọn — dùng cho dev/staging)
```
sql/seed/lab-seed.sql                     # nội dung lab mẫu (LAB-01, LAB-02 với steps + questions)
sql/seed/phase-7-test-accounts.sql        # tài khoản test cho pilot
```

## Diagnostic / Debug

Khi gặp lỗi (vd: admin tab không hiển thị tên sinh viên), chạy file trong `sql/diagnostic/` để kiểm tra trạng thái:

```
sql/diagnostic/lab-verification.sql                    # kiểm tra schema lab system phase 1
sql/diagnostic/2026-04-29-diagnose-profile-embed.sql   # debug PostgREST profile embed (FK + RLS)
```

## Quy tắc viết migration mới

1. **Đặt tên** theo format `YYYY-MM-DD-tên-mô-tả.sql` trong `sql/migrations/`
2. **Idempotent**: dùng `CREATE TABLE IF NOT EXISTS`, `ALTER TABLE ... DROP CONSTRAINT IF EXISTS ... ADD CONSTRAINT ...`, `CREATE OR REPLACE FUNCTION` …
3. **Reload PostgREST cache** ở cuối nếu đổi schema/RLS:
   ```sql
   NOTIFY pgrst, 'reload schema';
   ```
4. **Comment** rõ:
   - Mục đích migration (bug nào, feature gì)
   - Root cause (nếu là fix)
   - Có thể rollback không

## Liên kết

- Supabase project: `ylspcqbwupnqskqemmiv.supabase.co`
- SQL Editor: https://supabase.com/dashboard/project/ylspcqbwupnqskqemmiv/sql/new
