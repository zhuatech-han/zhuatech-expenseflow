-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

CREATE TABLE department (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE
);

CREATE TABLE access_role (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
  scope varchar(20) NOT NULL
);

CREATE TABLE permission (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL
);

CREATE TABLE role_permission (role_id bigint NOT NULL, permission_code varchar(60) NOT NULL, PRIMARY KEY(role_id, permission_code), FOREIGN KEY(role_id) REFERENCES access_role(id), FOREIGN KEY(permission_code) REFERENCES permission(code));

CREATE TABLE nav_menu (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  permission_code varchar(60) NOT NULL,
  position int NOT NULL,
  enabled boolean NOT NULL
);

CREATE TABLE account (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  username varchar(60) NOT NULL UNIQUE,
  display_name varchar(120) NOT NULL,
  password_hash varchar(100) NOT NULL,
  role_id bigint NOT NULL,
  department_id bigint NOT NULL,
  enabled boolean NOT NULL,
  FOREIGN KEY (role_id) REFERENCES access_role(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE TABLE audit_event (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  actor varchar(60) NOT NULL,
  action varchar(120) NOT NULL,
  object_id varchar(80) NOT NULL,
  department_id bigint NOT NULL,
  created_at timestamp(6) NOT NULL
);

CREATE TABLE system_setting (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  parameter_value varchar(200) NOT NULL
);

CREATE TABLE dictionary_entry (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  type varchar(60) NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  UNIQUE (type, code)
);



CREATE TABLE expense_budget (
 id bigint AUTO_INCREMENT PRIMARY KEY, version bigint NOT NULL, department_id bigint NOT NULL,
 category varchar(60) NOT NULL, budget_month varchar(7) NOT NULL,
 limit_cents bigint NOT NULL, reserved_cents bigint NOT NULL, committed_cents bigint NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id), UNIQUE(department_id,category,budget_month),
 CHECK(limit_cents>=0 AND reserved_cents>=0 AND committed_cents>=0)
);
CREATE TABLE expense_case (
 id bigint AUTO_INCREMENT PRIMARY KEY, version bigint NOT NULL, change_count bigint NOT NULL,
 number varchar(60) NOT NULL UNIQUE, kind varchar(20) NOT NULL, title varchar(200) NOT NULL,
 category varchar(60) NOT NULL, budget_month varchar(7) NOT NULL, department_id bigint NOT NULL,
 applicant_id bigint NOT NULL, manager_id bigint NULL, finance_id bigint NULL, application_id bigint NULL UNIQUE,
 status varchar(20) NOT NULL, amount_cents bigint NOT NULL, paid_cents bigint NOT NULL, reserved_cents bigint NOT NULL,
 submitted boolean NOT NULL, purpose varchar(3000) NOT NULL, created_at timestamp(6) NOT NULL, updated_at timestamp(6) NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id), FOREIGN KEY(applicant_id) REFERENCES account(id),
 FOREIGN KEY(manager_id) REFERENCES account(id), FOREIGN KEY(finance_id) REFERENCES account(id),
 FOREIGN KEY(application_id) REFERENCES expense_case(id),
 CHECK(kind IN ('APPLICATION','CLAIM')),
 CHECK(status IN ('DRAFT','MANAGER','FINANCE','APPROVED','PART_PAID','PAID','REJECTED','CANCELLED','CLOSED')),
 CHECK(amount_cents>=0 AND paid_cents>=0 AND reserved_cents>=0 AND paid_cents<=amount_cents)
);
CREATE TABLE expense_line (
 id bigint AUTO_INCREMENT PRIMARY KEY, case_id bigint NOT NULL, expense_date date NOT NULL,
 category varchar(60) NOT NULL, description varchar(500) NOT NULL, amount_cents bigint NOT NULL,
 issuer varchar(120) NOT NULL, reference varchar(120) NOT NULL, active_receipt_key varchar(64) NULL UNIQUE,
 FOREIGN KEY(case_id) REFERENCES expense_case(id), CHECK(amount_cents>0)
);
CREATE TABLE expense_evidence (
 id bigint AUTO_INCREMENT PRIMARY KEY, case_id bigint NOT NULL, content_type varchar(40) NOT NULL,
 extension varchar(8) NOT NULL, hash varchar(64) NOT NULL, size bigint NOT NULL, frozen boolean NOT NULL,
 payload longblob NOT NULL, created_at timestamp(6) NOT NULL,
 FOREIGN KEY(case_id) REFERENCES expense_case(id), CHECK(size>0 AND size<=5242880), UNIQUE(case_id,hash)
);
CREATE TABLE expense_event (
 id bigint AUTO_INCREMENT PRIMARY KEY, case_id bigint NOT NULL, actor varchar(60) NOT NULL, action varchar(60) NOT NULL,
 note text NOT NULL, snapshot text NOT NULL, created_at timestamp(6) NOT NULL, FOREIGN KEY(case_id) REFERENCES expense_case(id)
);
CREATE TABLE expense_payment (
 id bigint AUTO_INCREMENT PRIMARY KEY, case_id bigint NOT NULL, amount_cents bigint NOT NULL,
 reference varchar(120) NOT NULL, method varchar(60) NOT NULL, actor varchar(60) NOT NULL, payment_date date NOT NULL,
 created_at timestamp(6) NOT NULL, reversed_at timestamp(6) NULL, reversal_reason varchar(1000) NOT NULL,
 reversed_by varchar(60) NOT NULL, FOREIGN KEY(case_id) REFERENCES expense_case(id), UNIQUE(case_id,reference), CHECK(amount_cents>0)
);
CREATE TABLE expense_command (
 id bigint AUTO_INCREMENT PRIMARY KEY, case_id bigint NOT NULL, actor varchar(60) NOT NULL,
 request_key varchar(80) NOT NULL, fingerprint varchar(64) NOT NULL,
 FOREIGN KEY(case_id) REFERENCES expense_case(id), UNIQUE(case_id,actor,request_key)
);
