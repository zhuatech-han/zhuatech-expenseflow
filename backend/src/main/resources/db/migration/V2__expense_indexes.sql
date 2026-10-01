-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
ALTER TABLE nav_menu ADD CONSTRAINT fk_menu_permission FOREIGN KEY(permission_code) REFERENCES permission(code);
CREATE INDEX ix_case_scope ON expense_case(department_id,status,created_at);
CREATE INDEX ix_case_applicant ON expense_case(applicant_id,status);
CREATE INDEX ix_case_manager ON expense_case(manager_id,status);
CREATE INDEX ix_case_finance ON expense_case(finance_id,status);
CREATE INDEX ix_line_case ON expense_line(case_id);
CREATE INDEX ix_event_case ON expense_event(case_id,created_at);
CREATE INDEX ix_audit_scope ON audit_event(department_id,created_at);
