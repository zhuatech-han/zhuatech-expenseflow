<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { computed, onMounted, ref } from "vue";
import { api, resetCsrf } from "./api.js";
import {
  states,
  commands,
  labels,
  adminFields,
  scopeNames,
  errors,
  money,
  lineTotal,
  editable,
  date,
  eventName,
} from "./schema.js";
const lang = ref(localStorage.getItem("expenseflow-language") || "zh");
const me = ref(null),
  view = ref("workbench"),
  busy = ref(false),
  error = ref(""),
  notice = ref("");
const login = ref({ username: "", password: "" });
const options = ref({
  people: [],
  departments: [],
  dictionaries: [],
  settings: [],
  applications: [],
});
const directory = ref({ roles: [], departments: [], permissions: [] });
const rows = ref([]),
  total = ref(0),
  detail = ref(null),
  work = ref({ mine: [], approvals: [], payments: [] }),
  stats = ref({});
const modal = ref(null),
  form = ref({}),
  uploadFile = ref(null),
  search = ref(""),
  status = ref(""),
  kind = ref(""),
  category = ref(""),
  sort = ref("newest"),
  page = ref(0),
  historyId = ref(null);
const tr = (zh, en) => (lang.value === "zh" ? zh : en);
const pair = (v) => v?.[lang.value === "zh" ? 0 : 1];
const label = (k) => pair(labels[k]) || k;
const can = (p) => me.value?.permissions.includes(p);
const record = computed(() => detail.value?.record);
const mayEdit = computed(() => editable(record.value, me.value));
const zone = computed(
  () =>
    options.value.settings.find((s) => s.code === "timezone")?.value ||
    "Asia/Shanghai",
);
const company = computed(
  () =>
    options.value.settings.find((s) => s.code === "companyName")?.value ||
    "ExpenseFlow",
);
const person = (id) =>
  options.value.people.find((p) => p.id === id)?.displayName || `#${id}`;
const department = (id) =>
  options.value.departments.find((d) => d.id === id)?.name || `#${id}`;
const categoryName = (code, type = "category") => {
  const d = options.value.dictionaries.find(
    (d) => d.type === type && d.code === code,
  );
  return d ? (lang.value === "zh" ? d.name : d.nameEn) : code;
};
const stateName = (s) => pair(states[s]) || s;
const kindName = (k) =>
  k === "APPLICATION" ? tr("费用申请", "Application") : tr("费用报销", "Claim");
const menuName = (k) => {
  const m = me.value?.menus.find((m) => m.code === k);
  return m ? (lang.value === "zh" ? m.name : m.nameEn) : k;
};
const draftTotal = computed(() => lineTotal(form.value.lines || []));
const filteredAdmin = computed(() =>
  rows.value.filter(
    (r) =>
      !search.value ||
      [r.name, r.username, r.displayName, r.code, r.value].some((v) =>
        String(v || "")
          .toLowerCase()
          .includes(search.value.toLowerCase()),
      ),
  ),
);
const adminRows = computed(() =>
  filteredAdmin.value.slice(page.value * 10, page.value * 10 + 10),
);
const adminColumns = computed(() => [
  ...(["settings", "menus", "permissions"].includes(view.value)
    ? ["code"]
    : []),
  ...(adminFields[view.value] || []).filter((k) => k !== "password"),
]);
const displayRows = computed(() =>
  adminFields[view.value] ? adminRows.value : rows.value,
);
function language() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("expenseflow-language", lang.value);
}
function fail(e) {
  error.value =
    pair(errors[e.message]) ||
    tr("操作失败，请刷新后重试", "Operation failed. Refresh and retry.");
  if (e.message === "UNAUTHENTICATED") {
    me.value = null;
    detail.value = null;
    resetCsrf();
  }
}
async function run(task) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await task();
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function loadOptions() {
  options.value = await api("/options");
  if (can("admin"))
    for (const k of Object.keys(directory.value))
      directory.value[k] = await api("/admin/" + k);
}
async function loadView() {
  if (view.value === "cases") {
    const q = new URLSearchParams({
      search: search.value,
      status: status.value,
      kind: kind.value,
      category: category.value,
      page: page.value,
      size: 10,
      sort: sort.value,
    });
    const r = await api("/cases?" + q);
    rows.value = r.items;
    total.value = r.total;
  } else if (view.value === "workbench") work.value = await api("/workbench");
  else if (view.value === "dashboard") stats.value = await api("/dashboard");
  else if (["budgets", "audit"].includes(view.value))
    rows.value = await api("/" + view.value);
  else if (adminFields[view.value])
    rows.value = await api("/admin/" + view.value);
}
async function navigate(v) {
  await run(async () => {
    view.value = v;
    detail.value = null;
    rows.value = [];
    page.value = 0;
    search.value = "";
    await loadView();
  });
}
async function signIn() {
  await run(async () => {
    me.value = await api("/auth/login", "POST", login.value);
    login.value.password = "";
    resetCsrf();
    view.value = me.value.menus[0]?.code || "about";
    await loadOptions();
    await loadView();
  });
}
async function signOut() {
  await run(async () => {
    await api("/auth/logout", "POST");
    me.value = null;
    detail.value = null;
    modal.value = null;
    resetCsrf();
  });
}
async function openCase(id) {
  await run(async () => {
    detail.value = await api("/cases/" + id);
    historyId.value = null;
  });
}
function today() {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: zone.value,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}
function line() {
  return {
    expenseDate: today(),
    description: "",
    amount: "",
    issuer: "",
    reference: "",
  };
}
/** 构造独立表单，保留版本与命令幂等键。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function openModal(type, row = null, action = null) {
  error.value = "";
  notice.value = "";
  uploadFile.value = null;
  let fields = [],
    heading = "";
  let value = {};
  if (type === "draft" || type === "claimFrom") {
    const k =
      type === "claimFrom" ? "CLAIM" : row?.kind || action || "APPLICATION";
    heading =
      row && type === "draft" ? tr("修改草稿", "Edit draft") : kindName(k);
    fields = [
      "title",
      "category",
      "budgetMonth",
      ...(k === "APPLICATION" ? ["amount"] : ["applicationId"]),
      "managerId",
      "financeId",
      "purpose",
    ];
    value = {
      kind: k,
      title: "",
      category: "OFFICE",
      budgetMonth: today().slice(0, 7),
      amount: "",
      purpose: "",
      managerId: null,
      financeId: null,
      applicationId: null,
      lines: k === "CLAIM" ? [line()] : [],
      ...row,
    };
    if (row && type === "draft") {
      value.amount = (row.amountCents / 100).toFixed(2);
      value.lines = detail.value.lines.map((l) => ({
        ...l,
        amount: (l.amountCents / 100).toFixed(2),
      }));
    }
    if (type === "claimFrom") {
      value = {
        ...value,
        kind: "CLAIM",
        applicationId: row.id,
        version: null,
        lines: [line()],
      };
    }
  } else if (type === "command") {
    heading = pair(commands[action]);
    fields =
      action === "submit"
        ? []
        : action === "pay"
          ? ["amount", "reference", "method", "paymentDate", "note"]
          : action === "reverse"
            ? ["paymentId", "note"]
            : action === "finance-approve"
              ? ["note", "allowOverBudget"]
              : ["note"];
    value = {
      version: record.value.version,
      requestKey: crypto.randomUUID(),
      note: "",
      allowOverBudget: false,
      amount: (
        (record.value.amountCents - record.value.paidCents) /
        100
      ).toFixed(2),
      reference: "",
      method: "BANK",
      paymentDate: today(),
      paymentId: null,
    };
  } else if (type === "budget") {
    heading = tr("部门月预算", "Monthly budget");
    fields = ["departmentId", "category", "budgetMonth", "limit"];
    value = {
      departmentId: me.value.departmentId,
      category: "OFFICE",
      budgetMonth: today().slice(0, 7),
      limit: "",
      ...row,
    };
    if (row) value.limit = (row.limitCents / 100).toFixed(2);
  } else if (type === "admin") {
    heading = menuName(view.value) + (row?.code ? " · " + row.code : "");
    fields = adminFields[view.value];
    value = {
      enabled: true,
      scope: "DEPARTMENT",
      permissions: [],
      type: "category",
      ...row,
      password: "",
    };
  } else if (type === "password") {
    heading = tr("修改密码", "Change password");
    fields = ["oldPassword", "newPassword"];
    value = { oldPassword: "", newPassword: "" };
  } else if (type === "upload") {
    heading = tr("上传费用凭证", "Upload evidence");
  } else {
    heading = tr("确认删除", "Confirm deletion");
  }
  form.value = value;
  modal.value = { type, row, action, fields, heading };
}
function applyApplication() {
  const a = options.value.applications.find(
    (a) => a.id === form.value.applicationId,
  );
  if (a) {
    form.value.category = a.category;
    form.value.budgetMonth = a.budgetMonth;
  }
}
function choices(field) {
  if (field === "category" || field === "method")
    return options.value.dictionaries
      .filter((d) => d.type === (field === "category" ? "category" : "payment"))
      .map((d) => ({
        id: d.code,
        name: lang.value === "zh" ? d.name : d.nameEn,
      }));
  if (field === "departmentId") return options.value.departments;
  if (field === "roleId") return directory.value.roles;
  if (field === "scope")
    return Object.entries(scopeNames).map(([id, n]) => ({ id, name: pair(n) }));
  if (field === "permissionCode")
    return directory.value.permissions.map((p) => ({
      id: p.code,
      name: p.name,
    }));
  if (field === "managerId" || field === "financeId")
    return options.value.people
      .filter(
        (p) =>
          p.id !== me.value.id &&
          (field === "managerId"
            ? p.departmentId === me.value.departmentId &&
              p.permissions.includes("expense.manager")
            : p.id !== form.value.managerId &&
              p.scope === "ALL" &&
              p.permissions.includes("expense.finance")),
      )
      .map((p) => ({ id: p.id, name: p.displayName }));
  if (field === "applicationId") {
    const a = [...options.value.applications];
    if (
      detail.value?.application &&
      !a.some((a) => a.id === detail.value.application.id)
    )
      a.push(detail.value.application);
    return a.map((a) => ({
      id: a.id,
      name: a.number + " · " + a.title + " · ¥" + money(a.amountCents),
    }));
  }
  if (field === "paymentId")
    return detail.value.payments
      .filter((p) => !p.reversedAt)
      .map((p) => ({
        id: p.id,
        name: p.reference + " · ¥" + money(p.amountCents),
      }));
  return null;
}
function readonlyField(field) {
  return (
    (modal.value.type === "budget" &&
      modal.value.row &&
      ["departmentId", "category", "budgetMonth"].includes(field)) ||
    (modal.value.type === "claimFrom" &&
      ["category", "budgetMonth", "applicationId"].includes(field))
  );
}
function inputType(k) {
  return k.toLowerCase().includes("password")
    ? "password"
    : k === "paymentDate"
      ? "date"
      : k === "budgetMonth"
        ? "month"
        : ["amount", "limit", "position"].includes(k)
          ? "number"
          : "text";
}
function adminValue(r, k) {
  if (k === "roleId")
    return directory.value.roles.find((v) => v.id === r[k])?.name || r[k];
  if (k === "departmentId") return department(r[k]);
  if (k === "scope") return pair(scopeNames[r[k]]);
  if (k === "enabled")
    return r[k] ? tr("启用", "Active") : tr("停用", "Disabled");
  if (Array.isArray(r[k]))
    return r[k]
      .map(
        (code) =>
          directory.value.permissions.find((p) => p.code === code)?.name ||
          code,
      )
      .join(" · ");
  return r[k] ?? "—";
}
function snapshotText(s) {
  try {
    return JSON.stringify(JSON.parse(s), null, 2);
  } catch {
    return s;
  }
}
/** 保存真实单据、凭证、审批与管理资源，错误留在表单内。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function saveModal() {
  await run(async () => {
    const m = modal.value;
    let result;
    if (["draft", "claimFrom"].includes(m.type)) {
      const editing = m.type === "draft" && m.row;
      result = await api(
        "/cases" + (editing ? "/" + m.row.id : ""),
        editing ? "PUT" : "POST",
        {
          ...form.value,
          lines: form.value.kind === "CLAIM" ? form.value.lines : [],
        },
      );
    } else if (m.type === "command")
      result = await api(
        "/cases/" + record.value.id + "/commands/" + m.action,
        "POST",
        form.value,
      );
    else if (m.type === "budget")
      await api(
        "/budgets" + (m.row ? "/" + m.row.id : ""),
        m.row ? "PUT" : "POST",
        form.value,
      );
    else if (m.type === "admin") {
      const v = { ...form.value };
      if (!v.password) delete v.password;
      await api(
        "/admin/" + view.value + (m.row ? "/" + m.row.id : ""),
        m.row ? "PUT" : "POST",
        v,
      );
      me.value = await api("/auth/me");
    } else if (m.type === "deleteAdmin")
      await api("/admin/" + view.value + "/" + m.row.id, "DELETE");
    else if (m.type === "deleteCase") {
      await api(
        "/cases/" + record.value.id + "?version=" + record.value.version,
        "DELETE",
      );
      detail.value = null;
    } else if (m.type === "upload") {
      const data = new FormData();
      data.append("file", uploadFile.value);
      result = await api(
        "/cases/" +
          record.value.id +
          "/evidence?version=" +
          record.value.version,
        "POST",
        data,
      );
    } else if (m.type === "deleteEvidence")
      result = await api(
        "/cases/" +
          record.value.id +
          "/evidence/" +
          m.row.id +
          "?version=" +
          record.value.version,
        "DELETE",
      );
    else if (m.type === "password") {
      await api("/auth/password", "POST", form.value);
      me.value = null;
      detail.value = null;
      resetCsrf();
    }
    if (result?.record) detail.value = result;
    modal.value = null;
    form.value = {};
    if (me.value) {
      await loadOptions();
      await loadView();
    }
    notice.value = tr("已保存", "Saved");
  });
}
const projected = computed(() => {
  const b = detail.value?.budget;
  return b
    ? b.reservedCents +
        b.committedCents +
        record.value.amountCents -
        (detail.value.application?.reservedCents || 0)
    : null;
});
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    view.value = me.value.menus[0]?.code || "about";
    await loadOptions();
    await loadView();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") fail(e);
  }
});
</script>

<template>
  <div v-if="!me" class="login-page">
    <form class="login-card" @submit.prevent="signIn">
      <img class="brand-logo" src="/brand/logo.jpg" alt="知华科技" />
      <p class="eyebrow">EXPENSEFLOW</p>
      <h1>{{ tr("登录费用工作台", "Expense workspace") }}</h1>
      <p>
        {{
          tr(
            "费用申请 · 报销审批 · 付款登记",
            "Applications · Claims · Payment records",
          )
        }}
      </p>
      <label for="username">{{ tr("账号", "Username") }}</label
      ><input
        id="username"
        v-model="login.username"
        required
        autocomplete="username"
        maxlength="60"
      />
      <label for="password">{{ tr("密码", "Password") }}</label
      ><input
        id="password"
        v-model="login.password"
        required
        type="password"
        autocomplete="current-password"
        maxlength="128"
      />
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <button class="primary" :disabled="busy">
        {{ tr("登录", "Sign in") }}
      </button>
      <div class="login-footer">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >知华科技</a
        ><button class="link" type="button" @click="language">
          {{ lang === "zh" ? "English" : "中文" }}
        </button>
      </div>
      <small>{{
        tr(
          "公开源码学习版 · 未经书面授权不得商用",
          "Learning edition · Commercial use requires written authorization",
        )
      }}</small>
    </form>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" /><strong>ExpenseFlow</strong
        ><small>{{ tr("费用申请与报销", "Expenses & claims") }}</small>
      </div>
      <nav aria-label="Navigation">
        <button
          v-for="m in me.menus"
          :key="m.code"
          :class="{ active: view === m.code }"
          :disabled="busy"
          @click="navigate(m.code)"
        >
          {{ lang === "zh" ? m.name : m.nameEn }}
        </button>
      </nav>
      <div class="sidebar-footer">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >知华科技</a
        ><small>{{ tr("公开源码学习版", "Learning edition") }}</small>
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <span>{{ company }}</span>
        <div>
          <small>{{ me.displayName }}</small
          ><button class="link" @click="language">
            {{ lang === "zh" ? "English" : "中文" }}</button
          ><button class="link" @click="navigate('about')">
            {{ tr("关于", "About") }}</button
          ><button class="link" @click="openModal('password')">
            {{ tr("修改密码", "Password") }}</button
          ><button :disabled="busy" @click="signOut">
            {{ tr("退出", "Sign out") }}
          </button>
        </div>
      </header>
      <main>
        <p v-if="notice" class="notice" role="status">{{ notice }}</p>
        <p v-if="error && !modal" class="error" role="alert">{{ error }}</p>
        <template v-if="detail">
          <button class="back" :disabled="busy" @click="detail = null">
            ← {{ tr("返回列表", "Back") }}
          </button>
          <div class="page-heading">
            <div>
              <p class="eyebrow">
                {{ record.number }} · {{ kindName(record.kind) }}
              </p>
              <h1>{{ record.title }}</h1>
            </div>
            <span class="badge">{{ stateName(record.status) }}</span>
          </div>
          <div class="detail-grid">
            <section class="panel">
              <h2>{{ tr("费用信息", "Expense details") }}</h2>
              <dl class="metadata">
                <div>
                  <dt>{{ tr("申请人", "Applicant") }}</dt>
                  <dd>{{ person(record.applicantId) }}</dd>
                </div>
                <div>
                  <dt>{{ tr("部门", "Department") }}</dt>
                  <dd>{{ department(record.departmentId) }}</dd>
                </div>
                <div>
                  <dt>{{ tr("类别 / 月份", "Category / month") }}</dt>
                  <dd>
                    {{ categoryName(record.category) }} /
                    {{ record.budgetMonth }}
                  </dd>
                </div>
                <div>
                  <dt>{{ tr("金额（元）", "Amount (CNY)") }}</dt>
                  <dd class="amount">¥{{ money(record.amountCents) }}</dd>
                </div>
                <div>
                  <dt>{{ tr("主管", "Manager") }}</dt>
                  <dd>{{ person(record.managerId) }}</dd>
                </div>
                <div>
                  <dt>{{ tr("财务", "Finance") }}</dt>
                  <dd>{{ person(record.financeId) }}</dd>
                </div>
              </dl>
              <p class="purpose">{{ record.purpose }}</p>
              <div class="actions">
                <button
                  v-if="detail.application"
                  class="link"
                  @click="openCase(detail.application.id)"
                >
                  {{ tr("关联申请", "Application") }}:
                  {{ detail.application.number }}</button
                ><button
                  v-if="detail.claim"
                  class="link"
                  @click="openCase(detail.claim.id)"
                >
                  {{ tr("关联报销", "Claim") }}: {{ detail.claim.number }}
                </button>
              </div>
              <div class="actions">
                <button v-if="mayEdit" @click="openModal('draft', record)">
                  {{ tr("修改草稿", "Edit draft") }}</button
                ><button
                  v-for="a in detail.actions"
                  :key="a"
                  :disabled="busy"
                  :class="{
                    primary: [
                      'submit',
                      'manager-approve',
                      'finance-approve',
                      'pay',
                    ].includes(a),
                  }"
                  @click="openModal('command', null, a)"
                >
                  {{ pair(commands[a]) }}</button
                ><button
                  v-if="
                    record.kind === 'APPLICATION' &&
                    record.status === 'APPROVED' &&
                    record.applicantId === me.id &&
                    !detail.claim &&
                    can('expense.write')
                  "
                  @click="openModal('claimFrom', record)"
                >
                  {{ tr("由申请填报销", "Create linked claim") }}</button
                ><button
                  v-if="mayEdit && !record.submitted"
                  @click="openModal('deleteCase')"
                >
                  {{ tr("删除草稿", "Delete draft") }}</button
                ><a
                  v-if="can('export')"
                  class="button"
                  :href="'/api/cases/' + record.id + '/report.json'"
                  >{{ tr("导出 JSON", "Export JSON") }}</a
                >
              </div>
            </section>
            <section class="panel budget-summary">
              <h2>{{ tr("预算与付款", "Budget & payments") }}</h2>
              <template v-if="detail.budget"
                ><dl>
                  <div>
                    <dt>{{ tr("月预算额度", "Monthly limit") }}</dt>
                    <dd>¥{{ money(detail.budget.limitCents) }}</dd>
                  </div>
                  <div>
                    <dt>{{ tr("申请预留", "Reserved") }}</dt>
                    <dd>¥{{ money(detail.budget.reservedCents) }}</dd>
                  </div>
                  <div>
                    <dt>{{ tr("已批准费用", "Committed") }}</dt>
                    <dd>¥{{ money(detail.budget.committedCents) }}</dd>
                  </div>
                  <div>
                    <dt>{{ tr("可用预算", "Available") }}</dt>
                    <dd
                      :class="{
                        overdue:
                          detail.budget.limitCents -
                            detail.budget.reservedCents -
                            detail.budget.committedCents <
                          0,
                      }"
                    >
                      ¥{{
                        money(
                          detail.budget.limitCents -
                            detail.budget.reservedCents -
                            detail.budget.committedCents,
                        )
                      }}
                    </dd>
                  </div>
                </dl></template
              >
              <p v-else class="warning">
                {{ tr("尚未配置该月预算", "Budget not configured") }}
              </p>
              <template v-if="record.kind === 'CLAIM'"
                ><hr />
                <p>
                  {{ tr("已登记付款", "Recorded payment") }}
                  <strong>¥{{ money(record.paidCents) }}</strong>
                </p>
                <p>
                  {{ tr("待登记金额", "Outstanding") }}
                  <strong
                    >¥{{
                      money(
                        ["APPROVED", "PART_PAID", "PAID"].includes(
                          record.status,
                        )
                          ? record.amountCents - record.paidCents
                          : 0,
                      )
                    }}</strong
                  >
                </p></template
              ><small
                >{{ tr("更新时间", "Updated") }}
                {{ date(record.updatedAt, zone) }}</small
              >
            </section>
          </div>
          <section v-if="record.kind === 'CLAIM'" class="panel">
            <h2>{{ tr("报销明细", "Expense lines") }}</h2>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ tr("费用日期", "Date") }}</th>
                    <th>{{ tr("内容", "Description") }}</th>
                    <th>{{ tr("凭证开具方", "Issuer") }}</th>
                    <th>{{ tr("凭证编号", "Receipt reference") }}</th>
                    <th class="numeric">{{ tr("金额（元）", "CNY") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="l in detail.lines" :key="l.id">
                    <td>{{ l.expenseDate }}</td>
                    <td>{{ l.description }}</td>
                    <td>{{ l.issuer }}</td>
                    <td>{{ l.reference }}</td>
                    <td class="numeric">{{ money(l.amountCents) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <section class="panel">
            <div class="section-heading">
              <h2>{{ tr("费用凭证", "Evidence") }}</h2>
              <button v-if="mayEdit" @click="openModal('upload')">
                {{ tr("上传凭证", "Upload evidence") }}
              </button>
            </div>
            <p v-if="!detail.evidence.length" class="empty">
              {{ tr("暂无凭证", "No evidence") }}
            </p>
            <div v-for="e in detail.evidence" :key="e.id" class="attachment">
              <a :href="'/api/cases/' + record.id + '/evidence/' + e.id"
                >{{ tr("凭证", "Evidence") }} #{{ e.id }} ·
                {{ e.extension.toUpperCase() }}</a
              ><small
                >{{ (e.size / 1024).toFixed(1) }} KiB ·
                {{ date(e.createdAt, zone) }}</small
              ><span v-if="e.frozen" class="badge">{{
                tr("送审已冻结", "Frozen submission")
              }}</span
              ><button
                v-if="mayEdit && !e.frozen"
                class="link"
                @click="openModal('deleteEvidence', e)"
              >
                {{ tr("删除", "Delete") }}
              </button>
            </div>
          </section>
          <section v-if="detail.payments.length" class="panel">
            <h2>{{ tr("付款与冲销记录", "Payment & reversal records") }}</h2>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ tr("日期", "Date") }}</th>
                    <th>{{ tr("凭证编号 / 方式", "Reference / method") }}</th>
                    <th>{{ tr("登记人员", "Recorded by") }}</th>
                    <th class="numeric">{{ tr("金额（元）", "CNY") }}</th>
                    <th>{{ tr("状态", "Status") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="p in detail.payments" :key="p.id">
                    <td>{{ p.paymentDate }}</td>
                    <td>
                      {{ p.reference
                      }}<small>{{ categoryName(p.method, "payment") }}</small>
                    </td>
                    <td>{{ p.actor }}</td>
                    <td class="numeric">{{ money(p.amountCents) }}</td>
                    <td>
                      {{
                        p.reversedAt
                          ? tr("已冲销", "Reversed")
                          : tr("有效登记", "Active record")
                      }}<small v-if="p.reversedAt"
                        >{{ p.reversalReason }} ·
                        {{ date(p.reversedAt, zone) }}</small
                      >
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <section class="panel">
            <h2>{{ tr("审批与变更历史", "Review history") }}</h2>
            <ol class="history">
              <li v-for="e in detail.events" :key="e.id">
                <strong>{{ eventName(e.action, lang) }}</strong
                ><span>{{ e.actor }} · {{ date(e.createdAt, zone) }}</span>
                <p v-if="e.note">{{ e.note }}</p>
                <template v-if="e.snapshot"
                  ><button
                    class="link"
                    @click="historyId = historyId === e.id ? null : e.id"
                  >
                    {{ tr("查看送审快照", "Submission snapshot") }}
                  </button>
                  <pre v-if="historyId === e.id" class="snapshot">{{
                    snapshotText(e.snapshot)
                  }}</pre>
                </template>
              </li>
            </ol>
          </section>
        </template>
        <template v-else-if="view === 'workbench'"
          ><div class="page-heading">
            <div>
              <p class="eyebrow">WORKSPACE</p>
              <h1>{{ tr("我的费用待办", "My expense work") }}</h1>
            </div>
            <div class="actions">
              <button
                v-if="can('expense.write')"
                class="primary"
                @click="openModal('draft', null, 'APPLICATION')"
              >
                {{ tr("新建申请", "New application") }}</button
              ><button
                v-if="can('expense.write')"
                @click="openModal('draft', null, 'CLAIM')"
              >
                {{ tr("填报报销", "New claim") }}</button
              ><button :disabled="busy" @click="run(loadView)">
                {{ tr("刷新", "Refresh") }}
              </button>
            </div>
          </div>
          <section
            v-for="k in ['approvals', 'payments', 'mine']"
            :key="k"
            class="panel"
          >
            <div class="section-heading">
              <h2>
                {{
                  k === "approvals"
                    ? tr("待我审批", "My approvals")
                    : k === "payments"
                      ? tr("待登记付款", "Pending payments")
                      : tr("我的进行中单据", "My active expenses")
                }}
              </h2>
              <span class="badge">{{ work[k].length }}</span>
            </div>
            <p v-if="!work[k].length" class="empty">
              {{ tr("暂无待办", "No pending work") }}
            </p>
            <div v-else class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ tr("单据", "Expense") }}</th>
                    <th>{{ tr("申请人", "Applicant") }}</th>
                    <th>{{ tr("状态", "Status") }}</th>
                    <th class="numeric">{{ tr("金额（元）", "CNY") }}</th>
                    <th>{{ tr("更新时间", "Updated") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in work[k]" :key="r.id">
                    <td>
                      <button class="link record-link" @click="openCase(r.id)">
                        {{ r.title }}</button
                      ><small>{{ r.number }} · {{ kindName(r.kind) }}</small>
                    </td>
                    <td>{{ person(r.applicantId) }}</td>
                    <td>
                      <span class="badge">{{ stateName(r.status) }}</span>
                    </td>
                    <td class="numeric">{{ money(r.amountCents) }}</td>
                    <td>{{ date(r.updatedAt, zone) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section></template
        >
        <template v-else-if="view === 'cases'"
          ><div class="page-heading">
            <div>
              <p class="eyebrow">EXPENSES</p>
              <h1>{{ tr("费用单据", "Expenses") }}</h1>
            </div>
            <div class="actions">
              <button
                v-if="can('expense.write')"
                class="primary"
                @click="openModal('draft', null, 'APPLICATION')"
              >
                {{ tr("新建申请", "New application") }}</button
              ><button
                v-if="can('expense.write')"
                @click="openModal('draft', null, 'CLAIM')"
              >
                {{ tr("填报报销", "New claim") }}
              </button>
            </div>
          </div>
          <form
            class="panel filters"
            @submit.prevent="
              page = 0;
              run(loadView);
            "
          >
            <input
              v-model="search"
              :placeholder="tr('标题或单据编号', 'Title or number')"
              :aria-label="tr('搜索', 'Search')"
            /><select v-model="kind" :aria-label="tr('单据类型', 'Type')">
              <option value="">{{ tr("全部类型", "All types") }}</option>
              <option value="APPLICATION">{{ kindName("APPLICATION") }}</option>
              <option value="CLAIM">{{ kindName("CLAIM") }}</option></select
            ><select v-model="status" :aria-label="tr('状态', 'Status')">
              <option value="">{{ tr("全部状态", "All states") }}</option>
              <option v-for="(n, k) in states" :key="k" :value="k">
                {{ pair(n) }}
              </option></select
            ><select v-model="category" :aria-label="tr('类别', 'Category')">
              <option value="">{{ tr("全部类别", "All categories") }}</option>
              <option
                v-for="d in choices('category')"
                :key="d.id"
                :value="d.id"
              >
                {{ d.name }}
              </option></select
            ><select v-model="sort" :aria-label="tr('排序', 'Sort')">
              <option value="newest">{{ tr("最新创建", "Newest") }}</option>
              <option value="amount">
                {{ tr("金额降序", "Amount descending") }}
              </option>
              <option value="title">
                {{ tr("标题顺序", "Title") }}
              </option></select
            ><button :disabled="busy">{{ tr("查询", "Search") }}</button>
          </form>
          <section class="panel">
            <p v-if="!rows.length" class="empty">
              {{ tr("暂无匹配单据", "No matching expenses") }}
            </p>
            <div v-else class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ tr("单据", "Expense") }}</th>
                    <th>{{ tr("申请人 / 部门", "Applicant / department") }}</th>
                    <th>{{ tr("类别 / 月份", "Category / month") }}</th>
                    <th>{{ tr("状态", "Status") }}</th>
                    <th class="numeric">{{ tr("金额（元）", "CNY") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in rows" :key="r.id">
                    <td>
                      <button class="link record-link" @click="openCase(r.id)">
                        {{ r.title }}</button
                      ><small>{{ r.number }} · {{ kindName(r.kind) }}</small>
                    </td>
                    <td>
                      {{ person(r.applicantId)
                      }}<small>{{ department(r.departmentId) }}</small>
                    </td>
                    <td>
                      {{ categoryName(r.category)
                      }}<small>{{ r.budgetMonth }}</small>
                    </td>
                    <td>
                      <span class="badge">{{ stateName(r.status) }}</span>
                    </td>
                    <td class="numeric">{{ money(r.amountCents) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="pagination">
              <span
                >{{ tr("共", "Total") }} {{ total }} {{ tr("条", "records") }} ·
                {{ page + 1 }}</span
              ><button
                :disabled="busy || page === 0"
                @click="
                  page--;
                  run(loadView);
                "
              >
                {{ tr("上一页", "Previous") }}</button
              ><button
                :disabled="busy || (page + 1) * 10 >= total"
                @click="
                  page++;
                  run(loadView);
                "
              >
                {{ tr("下一页", "Next") }}
              </button>
            </div>
          </section></template
        >
        <template v-else-if="view === 'budgets'"
          ><div class="page-heading">
            <div>
              <p class="eyebrow">BUDGETS</p>
              <h1>{{ tr("部门月预算", "Monthly budgets") }}</h1>
            </div>
            <button
              v-if="can('expense.budget')"
              class="primary"
              @click="openModal('budget')"
            >
              {{ tr("设置预算", "New budget") }}
            </button>
          </div>
          <section class="panel">
            <p v-if="!rows.length" class="empty">
              {{ tr("尚未设置月预算", "No monthly budgets") }}
            </p>
            <div v-else class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ tr("部门 / 类别", "Department / category") }}</th>
                    <th>{{ tr("月份", "Month") }}</th>
                    <th class="numeric">{{ tr("额度（元）", "Limit") }}</th>
                    <th class="numeric">{{ tr("预留", "Reserved") }}</th>
                    <th class="numeric">{{ tr("已批准费用", "Committed") }}</th>
                    <th class="numeric">{{ tr("可用", "Available") }}</th>
                    <th>{{ tr("操作", "Actions") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="b in rows" :key="b.id">
                    <td>
                      {{ department(b.departmentId)
                      }}<small>{{ categoryName(b.category) }}</small>
                    </td>
                    <td>{{ b.budgetMonth }}</td>
                    <td class="numeric">{{ money(b.limitCents) }}</td>
                    <td class="numeric">{{ money(b.reservedCents) }}</td>
                    <td class="numeric">{{ money(b.committedCents) }}</td>
                    <td
                      class="numeric"
                      :class="{
                        overdue:
                          b.limitCents - b.reservedCents - b.committedCents < 0,
                      }"
                    >
                      {{
                        money(b.limitCents - b.reservedCents - b.committedCents)
                      }}
                    </td>
                    <td>
                      <button
                        v-if="can('expense.budget')"
                        class="link"
                        @click="openModal('budget', b)"
                      >
                        {{ tr("调整额度", "Edit limit") }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section></template
        >
        <template v-else-if="view === 'dashboard'"
          ><div class="page-heading">
            <div>
              <p class="eyebrow">OVERVIEW</p>
              <h1>{{ tr("费用统计", "Expense overview") }}</h1>
              <p>
                {{
                  tr(
                    "按当前账号数据范围统计",
                    "Statistics within your data scope",
                  )
                }}
              </p>
            </div>
            <button :disabled="busy" @click="run(loadView)">
              {{ tr("刷新", "Refresh") }}
            </button>
          </div>
          <div class="metrics">
            <article>
              <span>{{ tr("已批准报销（元）", "Approved claims (CNY)") }}</span
              ><strong>{{ money(stats.approvedCents) }}</strong>
            </article>
            <article>
              <span>{{
                tr("有效付款登记（元）", "Payment records (CNY)")
              }}</span
              ><strong>{{ money(stats.paidCents) }}</strong>
            </article>
            <article>
              <span>{{ tr("待登记付款（元）", "Outstanding (CNY)") }}</span
              ><strong>{{ money(stats.pendingCents) }}</strong>
            </article>
            <article>
              <span>{{ tr("超期审批单据", "Overdue reviews") }}</span
              ><strong>{{ stats.overdue ?? 0 }}</strong>
            </article>
          </div>
          <section class="panel">
            <h2>{{ tr("单据状态分布", "Expense states") }}</h2>
            <p v-if="!stats.count" class="empty">
              {{ tr("暂无费用单据", "No expenses") }}
            </p>
            <div
              v-for="(count, s) in stats.states"
              :key="s"
              class="status-line"
            >
              <span>{{ stateName(s) }}</span>
              <div>
                <i
                  :style="{
                    width: (count / Math.max(stats.count, 1)) * 100 + '%',
                  }"
                ></i>
              </div>
              <strong>{{ count }}</strong>
            </div>
          </section>
          <section class="panel">
            <h2>{{ tr("月预算占用", "Budget utilization") }}</h2>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>
                      {{
                        tr(
                          "部门 / 类别 / 月份",
                          "Department / category / month",
                        )
                      }}
                    </th>
                    <th class="numeric">{{ tr("额度", "Limit") }}</th>
                    <th class="numeric">
                      {{ tr("预留 + 已批准", "Reserved + committed") }}
                    </th>
                    <th class="numeric">{{ tr("可用", "Available") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="b in stats.budgets" :key="b.id">
                    <td>
                      {{ department(b.departmentId) }} ·
                      {{ categoryName(b.category) }} · {{ b.budgetMonth }}
                    </td>
                    <td class="numeric">{{ money(b.limitCents) }}</td>
                    <td class="numeric">
                      {{ money(b.reservedCents + b.committedCents) }}
                    </td>
                    <td class="numeric">
                      {{
                        money(b.limitCents - b.reservedCents - b.committedCents)
                      }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section></template
        >
        <template v-else-if="view === 'audit'"
          ><div class="page-heading">
            <h1>{{ tr("操作审计", "Audit history") }}</h1>
          </div>
          <section class="panel">
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ tr("时间", "Time") }}</th>
                    <th>{{ tr("账号", "Actor") }}</th>
                    <th>{{ tr("操作", "Action") }}</th>
                    <th>{{ tr("对象编号", "Object ID") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in rows" :key="r.id">
                    <td>{{ date(r.createdAt, zone) }}</td>
                    <td>{{ r.actor }}</td>
                    <td>{{ eventName(r.action, lang) }}</td>
                    <td>{{ r.objectId }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="!rows.length" class="empty">
              {{ tr("暂无审计记录", "No audit records") }}
            </p>
          </section></template
        >
        <template v-else-if="adminFields[view]"
          ><div class="page-heading">
            <div>
              <p class="eyebrow">ADMINISTRATION</p>
              <h1>{{ menuName(view) }}</h1>
            </div>
            <button
              v-if="!['menus', 'permissions', 'settings'].includes(view)"
              class="primary"
              @click="openModal('admin')"
            >
              {{ tr("新增", "Add") }}
            </button>
          </div>
          <section class="panel">
            <input
              v-model="search"
              :placeholder="
                tr('搜索名称、账号或代码', 'Search name, account or code')
              "
              :aria-label="tr('搜索管理资料', 'Search administration')"
              @input="page = 0"
            />
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th v-for="k in adminColumns" :key="k">
                      {{ label(k) }}
                    </th>
                    <th>{{ tr("操作", "Actions") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in displayRows" :key="r.id">
                    <td v-for="k in adminColumns" :key="k">
                      {{ adminValue(r, k) }}
                    </td>
                    <td>
                      <div class="actions">
                        <button class="link" @click="openModal('admin', r)">
                          {{ tr("编辑", "Edit") }}</button
                        ><button
                          v-if="
                            !['menus', 'permissions', 'settings'].includes(view)
                          "
                          class="link"
                          @click="openModal('deleteAdmin', r)"
                        >
                          {{ tr("删除", "Delete") }}
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="!displayRows.length" class="empty">
              {{ tr("暂无匹配资料", "No matching records") }}
            </p>
            <div class="pagination">
              <span>{{ filteredAdmin.length }} · {{ page + 1 }}</span
              ><button :disabled="!page || busy" @click="page--">
                {{ tr("上一页", "Previous") }}</button
              ><button
                :disabled="(page + 1) * 10 >= filteredAdmin.length || busy"
                @click="page++"
              >
                {{ tr("下一页", "Next") }}
              </button>
            </div>
          </section></template
        >
        <section v-else class="panel about">
          <img src="/brand/logo.jpg" alt="知华科技" />
          <h1>ExpenseFlow</h1>
          <p>
            {{
              tr(
                "企业费用申请与报销系统",
                "Enterprise expense applications and claims",
              )
            }}
          </p>
          <p>
            {{
              tr(
                "公开源码学习版／非商业源码版。未经书面授权不得商用。",
                "Learning / non-commercial source edition. Commercial use requires written authorization.",
              )
            }}
          </p>
          <p>
            知华科技 · 上海如静知华信息科技有限公司<br /><a
              href="https://www.zhuatech.cn/"
              target="_blank"
              rel="noopener"
              >https://www.zhuatech.cn/</a
            ><br />{{
              tr(
                "商业授权、定制开发、部署与系统集成咨询微信",
                "Licensing, development, deployment and integration WeChat",
              )
            }}：zhuatech / zhuatech2
          </p>
          <p>
            {{
              tr(
                "付款为人工登记，不执行银行转账；凭证编号由填报人录入，不执行票据认证。",
                "Payments are manual records. No bank transfer or receipt authentication is performed.",
              )
            }}
          </p>
          <a href="/third-party/NOTICES.txt" target="_blank" rel="noopener">{{
            tr("第三方版权与许可", "Third-party notices")
          }}</a>
        </section>
      </main>
    </div>
  </div>
  <div v-if="modal" class="modal-overlay" @click.self="!busy && (modal = null)">
    <form
      class="modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
      @submit.prevent="saveModal"
    >
      <div class="section-heading">
        <h2 id="modal-title">{{ modal.heading }}</h2>
        <button
          type="button"
          :disabled="busy"
          :aria-label="tr('关闭', 'Close')"
          @click="modal = null"
        >
          ×
        </button>
      </div>
      <div class="form-grid">
        <div
          v-for="k in modal.fields"
          :key="k"
          :class="{
            wide: [
              'purpose',
              'note',
              'permissions',
              'allowOverBudget',
            ].includes(k),
          }"
        >
          <label :for="'field-' + k">{{ label(k) }}</label>
          <fieldset v-if="k === 'permissions'">
            <label
              v-for="p in directory.permissions"
              :key="p.id"
              class="checkbox-label"
              ><input
                v-model="form.permissions"
                type="checkbox"
                :value="p.code"
              />{{ p.name }}</label
            >
          </fieldset>
          <input
            v-else-if="['enabled', 'allowOverBudget'].includes(k)"
            :id="'field-' + k"
            v-model="form[k]"
            type="checkbox"
          />
          <select
            v-else-if="choices(k)"
            :id="'field-' + k"
            v-model="form[k]"
            :required="k !== 'applicationId'"
            :disabled="readonlyField(k)"
            @change="k === 'applicationId' && applyApplication()"
          >
            <option :value="null">{{ tr("请选择", "Choose") }}</option>
            <option v-for="o in choices(k)" :key="o.id" :value="o.id">
              {{ o.name }}
            </option>
          </select>
          <textarea
            v-else-if="['purpose', 'note'].includes(k)"
            :id="'field-' + k"
            v-model="form[k]"
            required
            rows="3"
            :maxlength="k === 'purpose' ? 3000 : 1000"
          ></textarea>
          <input
            v-else
            :id="'field-' + k"
            v-model="form[k]"
            :type="inputType(k)"
            :required="
              k !== 'password' ||
              (modal.type === 'admin' && !modal.row && view === 'users')
            "
            :disabled="readonlyField(k)"
            :step="['amount', 'limit'].includes(k) ? '0.01' : undefined"
            :min="k === 'limit' ? 0 : k === 'amount' ? 0.01 : undefined"
            :max="
              ['amount', 'limit'].includes(k)
                ? 100000000
                : k === 'paymentDate'
                  ? today()
                  : undefined
            "
            :maxlength="
              ['title'].includes(k)
                ? 200
                : k.toLowerCase().includes('password')
                  ? 128
                  : k === 'value'
                    ? 1000
                    : 120
            "
            :autocomplete="inputType(k) === 'password' ? 'new-password' : 'off'"
          />
        </div>
      </div>
      <section
        v-if="
          ['draft', 'claimFrom'].includes(modal.type) && form.kind === 'CLAIM'
        "
        class="line-editor"
      >
        <div class="section-heading">
          <h3>{{ tr("报销明细", "Expense lines") }}</h3>
          <button
            type="button"
            :disabled="form.lines.length >= 20"
            @click="form.lines.push(line())"
          >
            + {{ tr("添加明细", "Add line") }}
          </button>
        </div>
        <fieldset v-for="(l, i) in form.lines" :key="i">
          <legend>{{ tr("明细", "Line") }} {{ i + 1 }}</legend>
          <div class="form-grid">
            <div>
              <label :for="'date-' + i">{{
                tr("费用日期", "Expense date")
              }}</label
              ><input
                :id="'date-' + i"
                v-model="l.expenseDate"
                type="date"
                required
                :max="today()"
              />
            </div>
            <div>
              <label :for="'amount-' + i">{{ label("amount") }}</label
              ><input
                :id="'amount-' + i"
                v-model="l.amount"
                type="number"
                required
                min="0.01"
                max="100000000"
                step="0.01"
              />
            </div>
            <div class="wide">
              <label :for="'description-' + i">{{
                tr("费用内容", "Description")
              }}</label
              ><input
                :id="'description-' + i"
                v-model="l.description"
                required
                maxlength="500"
              />
            </div>
            <div>
              <label :for="'issuer-' + i">{{
                tr("凭证开具方", "Receipt issuer")
              }}</label
              ><input
                :id="'issuer-' + i"
                v-model="l.issuer"
                required
                maxlength="120"
              />
            </div>
            <div>
              <label :for="'reference-' + i">{{
                tr("凭证编号", "Receipt reference")
              }}</label
              ><input
                :id="'reference-' + i"
                v-model="l.reference"
                required
                maxlength="120"
              />
            </div>
          </div>
          <button
            type="button"
            class="link"
            :disabled="form.lines.length === 1"
            @click="form.lines.splice(i, 1)"
          >
            {{ tr("移除明细", "Remove line") }}
          </button>
        </fieldset>
        <p class="line-total">
          {{ tr("合计（元）", "Total (CNY)") }}：¥{{ money(draftTotal) }}
        </p>
      </section>
      <template v-if="modal.type === 'upload'"
        ><label for="evidence-file">{{ tr("凭证文件", "Evidence file") }}</label
        ><input
          id="evidence-file"
          type="file"
          required
          accept=".png,.jpg,.jpeg,.pdf"
          @change="uploadFile = $event.target.files[0]"
        />
        <p class="hint">
          PNG / JPEG / PDF ·
          {{
            tr(
              "单份不超过 5 MiB，最多 20 份",
              "Up to 5 MiB each; at most 20 files",
            )
          }}
        </p></template
      >
      <p
        v-if="
          modal.type === 'command' &&
          modal.action === 'finance-approve' &&
          detail.budget
        "
        class="warning"
      >
        {{ tr("批准后预留及费用占用", "Reserved + committed after approval") }}
        ¥{{ money(projected) }} / {{ tr("预算额度", "Limit") }} ¥{{
          money(detail.budget.limitCents)
        }}
      </p>
      <p
        v-if="modal.type === 'command' && modal.action === 'submit'"
        class="warning"
      >
        {{
          tr(
            "提交后保存明细快照并冻结现有凭证。",
            "Submission preserves a snapshot and freezes existing evidence.",
          )
        }}
      </p>
      <p
        v-if="
          modal.type === 'command' && ['pay', 'reverse'].includes(modal.action)
        "
        class="hint"
      >
        {{
          tr(
            "请核对实际付款凭证。此操作仅登记或冲销系统记录。",
            "Check the external payment evidence. This changes system records only.",
          )
        }}
      </p>
      <p v-if="modal.type.startsWith('delete')" class="warning">
        {{
          tr(
            "删除后无法从本页面恢复。仍被引用或已送审的资料不能删除。",
            "Deletion cannot be undone here. Referenced or submitted records are protected.",
          )
        }}
      </p>
      <p
        v-if="
          modal.type === 'password' ||
          (modal.type === 'admin' && view === 'users')
        "
        class="hint"
      >
        {{ pair(errors.WEAK_PASSWORD) }}
      </p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div class="modal-actions">
        <button type="button" :disabled="busy" @click="modal = null">
          {{ tr("取消", "Cancel") }}</button
        ><button
          class="primary"
          :disabled="
            busy ||
            (['draft', 'claimFrom'].includes(modal.type) &&
              form.kind === 'CLAIM' &&
              draftTotal === null)
          "
        >
          {{ tr("确认保存", "Save") }}
        </button>
      </div>
    </form>
  </div>
</template>
