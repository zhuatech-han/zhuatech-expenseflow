// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { lineTotal, money, editable } from "./schema.js";
test("decimal sum is exact in cents", () =>
  assert.equal(
    lineTotal([{ amount: "0.1" }, { amount: "0.2" }, { amount: "80.01" }]),
    8031,
  ));
test("subcent and malformed amounts are rejected", () => {
  assert.equal(lineTotal([{ amount: "1.001" }]), null);
  assert.equal(lineTotal([{ amount: "-1" }]), null);
  assert.equal(lineTotal([{ amount: "" }]), null);
});
test("large line total stays within service limit", () =>
  assert.equal(lineTotal([{ amount: "100000000" }, { amount: "0.01" }]), null));
test("currency renders zeros and invalid values accurately", () => {
  assert.equal(money(0), "0.00");
  assert.equal(money(1001), "10.01");
  assert.equal(money(undefined), "—");
});
test("other applicants cannot see edit action", () =>
  assert.equal(
    editable(
      { applicantId: 1, status: "DRAFT" },
      { id: 2, permissions: ["expense.write"] },
    ),
    false,
  ));
test("approved and partially paid claims cannot be edited", () => {
  for (const status of ["APPROVED", "PART_PAID", "PAID"])
    assert.equal(
      editable(
        { applicantId: 1, status },
        { id: 1, permissions: ["expense.write"] },
      ),
      false,
    );
  assert.equal(
    editable(
      { applicantId: 1, status: "REJECTED" },
      { id: 1, permissions: ["expense.write"] },
    ),
    true,
  );
});
