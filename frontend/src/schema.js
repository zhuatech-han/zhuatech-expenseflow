// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
export const scopeNames = {
  ALL: ["全部数据", "All data"],
  DEPARTMENT: ["本部门", "Department"],
  ASSIGNED: ["本人关联", "Assigned"],
};
export const adminFields = {
  users: [
    "username",
    "displayName",
    "password",
    "roleId",
    "departmentId",
    "enabled",
  ],
  roles: ["name", "scope", "permissions"],
  departments: ["name"],
  menus: ["name", "nameEn", "permissionCode", "position", "enabled"],
  permissions: ["name"],
  dictionaries: ["type", "code", "name", "nameEn"],
  settings: ["value"],
};
export const errors = {
  NETWORK_ERROR: ["连接失败，请稍后重试", "Connection failed. Retry."],
  UNAUTHENTICATED: ["登录已失效，请重新登录", "Session expired. Sign in."],
  LOGIN_FAILED: [
    "账号、密码错误或账号停用",
    "Invalid credentials or disabled account.",
  ],
  LOGIN_THROTTLED: ["请五分钟后重试登录", "Retry login in five minutes."],
  FORBIDDEN: ["没有操作权限", "Permission denied."],
  OUT_OF_SCOPE: ["没有该记录的数据权限", "Outside your data scope."],
  INVALID_INPUT: [
    "请检查必填项、长度和日期",
    "Check required fields, lengths and dates.",
  ],
  INVALID_STATE: ["状态已改变，请刷新详情", "State changed. Refresh detail."],
  STALE_VERSION: [
    "记录已更新，请刷新后操作",
    "Record changed. Refresh detail.",
  ],
  INDEPENDENT_REVIEW_REQUIRED: [
    "复核人必须独立于报告与执行人员",
    "Reviewer must be independent of reporting and execution.",
  ],
  INVALID_ASSIGNEE: [
    "责任人须启用、属于本部门且具有相应权限",
    "Assignee must be active, in the same department and authorized.",
  ],
  NOT_REVIEWER: [
    "仅指定复核人可操作",
    "Only the designated reviewer may do this.",
  ],
  INVALID_DICTIONARY: ["来源或类别不存在", "Unknown source or category."],
  IDEMPOTENCY_CONFLICT: [
    "请求内容已改变，请重新打开操作",
    "Request payload changed. Reopen operation.",
  ],
  CONFLICT: ["数据重复或仍被其他记录引用", "Duplicate or referenced data."],
  LAST_ADMIN: [
    "须保留一个启用的全范围管理员",
    "Keep one active all-data administrator.",
  ],
  WEAK_PASSWORD: [
    "密码至少 12 位、含大写小写及数字，UTF-8 不超过 72 字节",
    "Use at least 12 characters with upper case, lower case and digits; UTF-8 up to 72 bytes.",
  ],
  OLD_PASSWORD_INVALID: ["当前密码不正确", "Current password is incorrect."],
  BUILTIN_RESOURCE: [
    "内建资源不能删除",
    "Built-in resource cannot be deleted.",
  ],
  NOT_FOUND: ["记录不存在", "Record not found."],
  INVALID_USERNAME: [
    "账号须为 3–60 位字母、数字、点、横线或下划线",
    "Invalid username.",
  ],
  INVALID_SCOPE: ["数据范围不正确", "Invalid data scope."],
  INVALID_PERMISSION: ["权限代码不存在", "Unknown permission code."],
  REGISTERED_PERMISSIONS_ONLY: [
    "只能编辑已登记权限的名称",
    "Only registered permission names may be edited.",
  ],
  REGISTERED_MENUS_ONLY: [
    "只能编辑已登记菜单",
    "Only registered menus may be edited.",
  ],
  REGISTERED_SETTINGS_ONLY: [
    "只能编辑已登记参数",
    "Only registered settings may be edited.",
  ],
  INVALID_SETTING: ["参数不正确", "Invalid setting."],
  INVALID_REQUEST_KEY: ["请重新打开操作", "Reopen the operation."],
};

export const states = {
  DRAFT: ["草稿", "Draft"],
  MANAGER: ["待主管审批", "Manager review"],
  FINANCE: ["待财务复核", "Finance review"],
  APPROVED: ["已批准", "Approved"],
  PART_PAID: ["部分付款登记", "Partially paid"],
  PAID: ["已付款登记", "Payment recorded"],
  REJECTED: ["退回补充", "Returned"],
  CANCELLED: ["已取消", "Cancelled"],
  CLOSED: ["申请已关闭", "Closed"],
};
export const commands = {
  submit: ["提交审批", "Submit"],
  recall: ["撤回送审", "Recall"],
  "manager-approve": ["主管通过", "Manager approve"],
  "finance-approve": ["财务批准", "Finance approve"],
  reject: ["退回补充", "Return"],
  cancel: ["取消单据", "Cancel"],
  close: ["关闭申请", "Close application"],
  pay: ["登记付款", "Record payment"],
  reverse: ["冲销付款登记", "Reverse payment record"],
};
export const labels = {
  title: ["事由标题", "Title"],
  category: ["费用类别", "Category"],
  budgetMonth: ["预算月份", "Budget month"],
  amount: ["人民币金额（元）", "CNY amount"],
  purpose: ["费用用途", "Business purpose"],
  managerId: ["主管审批人", "Manager"],
  financeId: ["财务复核人", "Finance reviewer"],
  applicationId: ["关联已批准申请", "Approved application"],
  note: ["意见或原因", "Note / reason"],
  reference: ["付款凭证编号", "Payment reference"],
  method: ["付款方式", "Method"],
  paymentDate: ["付款日期", "Payment date"],
  paymentId: ["待冲销付款", "Payment to reverse"],
  allowOverBudget: ["确认批准超预算并记录原因", "Confirm over-budget approval"],
  limit: ["预算额度（元）", "Budget limit"],
  departmentId: ["部门", "Department"],
  name: ["名称", "Name"],
  nameEn: ["英文名称", "English name"],
  username: ["登录账号", "Username"],
  displayName: ["显示名称", "Display name"],
  password: ["初始或重置密码", "Initial / reset password"],
  roleId: ["角色", "Role"],
  enabled: ["启用", "Enabled"],
  scope: ["数据范围", "Scope"],
  permissions: ["权限", "Permissions"],
  permissionCode: ["菜单所需权限", "Permission"],
  position: ["菜单顺序", "Position"],
  type: ["字典类型", "Dictionary type"],
  code: ["代码", "Code"],
  value: ["参数值", "Value"],
  oldPassword: ["当前密码", "Current password"],
  newPassword: ["新密码", "New password"],
};
Object.assign(errors, {
  INVALID_AMOUNT: [
    "金额须大于零，最多两位小数且不超过一亿元",
    "Use a positive amount, at most two decimals, up to CNY 100 million.",
  ],
  INVALID_MONTH: ["请选择有效预算月份", "Choose a valid budget month."],
  INDEPENDENT_REVIEW_REQUIRED: [
    "申请人、主管和财务须为不同人员",
    "Applicant, manager and finance must be different people.",
  ],
  INVALID_ASSIGNEE: [
    "请选择启用的同部门主管及全范围财务人员",
    "Choose an active department manager and all-data finance reviewer.",
  ],
  NOT_APPLICANT: ["仅申请人本人可操作", "Applicant only."],
  NOT_APPROVER: ["仅指定审批人可操作", "Designated approver only."],
  NOT_PAYER: [
    "付款登记须由独立付款岗位执行",
    "An independent payer must record payment.",
  ],
  BUDGET_REQUIRED: [
    "该部门、类别和月份尚未设置预算",
    "Set the department/category/month budget first.",
  ],
  OVER_BUDGET_CONFIRMATION_REQUIRED: [
    "超出预算，请明确确认并填写批准原因",
    "Confirm over-budget approval and enter a reason.",
  ],
  BUDGET_BELOW_COMMITTED: [
    "预算额度不能低于已批准费用与预留合计",
    "Limit cannot be below committed and reserved amounts.",
  ],
  BUDGET_KEY_IMMUTABLE: [
    "已有预算的部门、类别和月份不能修改",
    "Budget key cannot be changed.",
  ],
  INVALID_APPLICATION: [
    "关联申请不属于本人、已关闭或可用预留不足",
    "Application is unavailable or outside your scope.",
  ],
  APPLICATION_LIMIT: [
    "报销金额不能超过申请批准额度",
    "Claim cannot exceed approved application amount.",
  ],
  LINKED_CLAIM_EXISTS: [
    "关联报销仍未完成，请先处理报销",
    "Finish or cancel the linked claim first.",
  ],
  LINES_REQUIRED: ["请填写 1–20 条报销明细", "Enter 1–20 expense lines."],
  INVALID_EXPENSE_DATE: [
    "费用日期不得晚于今天，且须属于预算月份",
    "Expense date must match budget month and not be in the future.",
  ],
  DUPLICATE_RECEIPT: ["同一凭证不能重复填入", "Do not reuse the same receipt."],
  EVIDENCE_REQUIRED: [
    "报销送审前至少上传一份凭证",
    "Upload evidence before submitting a claim.",
  ],
  INVALID_FILE: [
    "仅支持文件头匹配的 PNG、JPEG、PDF，单份不超过 5 MiB，最多 20 份",
    "Use signature-matched PNG/JPEG/PDF up to 5 MiB; at most 20 files.",
  ],
  HISTORY_PROTECTED: [
    "送审历史和冻结凭证不能删除",
    "Submitted history and frozen evidence cannot be deleted.",
  ],
  OVERPAYMENT: [
    "付款金额超出当前待付款余额",
    "Payment exceeds outstanding amount.",
  ],
  INVALID_PAYMENT: [
    "付款记录不存在、已冲销或不属于当前单据",
    "Payment is unavailable or already reversed.",
  ],
  INVALID_PAYMENT_DATE: [
    "付款日期不得晚于今天",
    "Payment date cannot be in the future.",
  ],
  REPORT_LIMIT: [
    "汇总超过一万条，请缩小数据范围",
    "Report exceeds 10,000 records.",
  ],
  CONFLICT: [
    "编号重复、凭证已被其他单据引用或资料仍被使用",
    "Duplicate reference, claimed receipt or referenced resource.",
  ],
});
/** 精确显示整数分，保持单据金额和汇总一致。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function money(cents) {
  if (!Number.isSafeInteger(cents)) return "—";
  return (cents / 100).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}
/** 前端明细合计使用整数分，非法和超过两位小数不参与合计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function lineTotal(lines) {
  let total = 0n;
  for (const l of lines) {
    const s = String(l.amount ?? "");
    if (!/^\d+(\.\d{1,2})?$/.test(s)) return null;
    const [whole, frac = ""] = s.split(".");
    const n = BigInt(whole) * 100n + BigInt(frac.padEnd(2, "0"));
    if (n <= 0n) return null;
    total += n;
  }
  return total <= 10000000000n ? Number(total) : null;
}
/** 只有申请人的可修改状态显示编辑入口；服务端另行检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function editable(record, me) {
  return (
    !!record &&
    !!me &&
    record.applicantId === me.id &&
    ["DRAFT", "REJECTED"].includes(record.status) &&
    me.permissions.includes("expense.write")
  );
}
/** 按系统时区显示留痕时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function date(value, zone = "Asia/Shanghai") {
  return value
    ? new Intl.DateTimeFormat("zh-CN", {
        dateStyle: "short",
        timeStyle: "short",
        timeZone: zone,
      }).format(new Date(value))
    : "—";
}
/** 把业务动作与审计代码显示为可读名称。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function eventName(code, lang = "zh") {
  const fixed = {
    CREATE: ["新建单据", "Create"],
    SAVE: ["保存草稿", "Save"],
    SUBMIT: commands.submit,
    RECALL: commands.recall,
    MANAGER_APPROVE: commands["manager-approve"],
    FINANCE_APPROVE: commands["finance-approve"],
    REJECT: commands.reject,
    CANCEL: commands.cancel,
    CLOSE: commands.close,
    PAY: commands.pay,
    REVERSE: commands.reverse,
    UPLOAD_EVIDENCE: ["上传凭证", "Upload evidence"],
    DELETE_EVIDENCE: ["删除未送审凭证", "Delete evidence"],
    CLAIM_COMMITTED: ["申请转报销占用", "Commit claim"],
    LOGIN: ["登录", "Sign in"],
    PASSWORD_CHANGE: ["修改密码", "Change password"],
    BUDGET_UPDATE: ["调整预算", "Update budget"],
    DELETE_DRAFT: ["删除草稿", "Delete draft"],
  };
  return (
    fixed[code]?.[lang === "zh" ? 0 : 1] ||
    (code.startsWith("ADMIN_")
      ? lang === "zh"
        ? "管理资料变更"
        : "Administration update"
      : code)
  );
}
