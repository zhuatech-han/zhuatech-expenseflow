// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.expenseflow;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

/** 申请、报销、预算占用、凭证、独立审批和付款冲销的真实事务服务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class ExpenseService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

  public ExpenseService(Store db, AccessService access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** 草稿输入；本人和部门由会话确定，金额不由客户端累计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Draft(
      Long version,
      String kind,
      String title,
      String category,
      String budgetMonth,
      BigDecimal amount,
      String purpose,
      Long managerId,
      Long financeId,
      Long applicationId,
      List<LineInput> lines) {}

  /** 费用明细与人工凭证引用；每行仅使用单据的预算类别与月份。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record LineInput(
      LocalDate expenseDate,
      String description,
      BigDecimal amount,
      String issuer,
      String reference) {}

  /** 费用命令需要版本和幂等键；超预算批准必须显式确认并写原因。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(
      Long version,
      String requestKey,
      String note,
      Boolean allowOverBudget,
      BigDecimal amount,
      String reference,
      String method,
      LocalDate paymentDate,
      Long paymentId) {}

  /** 部门月预算输入，修改必须匹配版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record BudgetInput(
      Long version, Long departmentId, String category, String budgetMonth, BigDecimal limit) {}

  /** 返回必要的同部门主管与全范围财务目录，隐藏密码和用户名。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.require("expense.read");
    return Map.of(
        "people",
        db.all(Account.class).stream()
            .filter(
                a ->
                    a.enabled
                        && (access.visible(a.departmentId)
                            || role(a).scope.equals("ALL")
                                && role(a).permissions.contains("expense.finance")))
            .map(
                a ->
                    Map.of(
                        "id",
                        a.id,
                        "displayName",
                        a.displayName,
                        "departmentId",
                        a.departmentId,
                        "permissions",
                        role(a).permissions,
                        "scope",
                        role(a).scope))
            .toList(),
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList(),
        "dictionaries",
        db.all(DictionaryEntry.class),
        "settings",
        db.all(SystemSetting.class),
        "applications",
        db
            .query(
                ExpenseCase.class,
                "from ExpenseCase where kind='APPLICATION' and status='APPROVED' and applicantId=?1 and reservedCents>0",
                access.current().id)
            .stream()
            .filter(c -> linked(c.id) == null)
            .toList());
  }

  /** 创建本人单据；草稿不占预算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object create(Draft v) {
    access.require("expense.write");
    var me = access.current();
    db.lock(Department.class, me.departmentId);
    var c = new ExpenseCase();
    c.departmentId = me.departmentId;
    c.applicantId = me.id;
    c.kind = v.kind();
    if (c.kind == null || !Set.of("APPLICATION", "CLAIM").contains(c.kind))
      throw new Problem(400, "INVALID_INPUT");
    c.number = "EXP-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
    c.status = "DRAFT";
    c.createdAt = clock.instant();
    c.updatedAt = c.createdAt;
    c.title = text(v.title(), 200);
    c.category = text(v.category(), 60);
    c.budgetMonth = ExpensePolicy.month(v.budgetMonth());
    c.purpose = text(v.purpose(), 3000);
    db.save(c);
    fill(c, v);
    event(c, "CREATE", "", false);
    db.flush();
    return detail(c.id);
  }

  /** 保存本人草稿或退回单，保留之前的送审快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(Long id, Draft v) {
    access.require("expense.write");
    var c = lock(id);
    author(c);
    ExpensePolicy.state(c, "DRAFT", "REJECTED");
    ExpensePolicy.version(c, v.version());
    fill(c, v);
    event(c, "SAVE", "", false);
    db.flush();
    return detail(id);
  }

  /** 只删除从未送审草稿及其未冻结附件；有历史的单据用取消保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(Long id, Long version) {
    access.require("expense.write");
    var c = lock(id);
    author(c);
    ExpensePolicy.version(c, version);
    ExpensePolicy.state(c, "DRAFT");
    if (c.submitted) throw new Problem(409, "HISTORY_PROTECTED");
    for (var e : evidence(id)) db.delete(e);
    for (var e : lines(id)) db.delete(e);
    for (var e : events(id)) db.delete(e);
    access.audit("DELETE_DRAFT", id, c.departmentId);
    db.delete(c);
  }

  /** 数据范围内搜索、筛选、数据库分页及固定排序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(
      String search, String status, String kind, String category, int page, int size, String sort) {
    access.require("expense.read");
    if (search.length() > 200
        || category.length() > 60
        || page < 0
        || page > 100000
        || size < 1
        || size > 100
        || !Set.of("", "APPLICATION", "CLAIM").contains(kind)
        || !Set.of(
                "",
                "DRAFT",
                "MANAGER",
                "FINANCE",
                "APPROVED",
                "PART_PAID",
                "PAID",
                "REJECTED",
                "CANCELLED",
                "CLOSED")
            .contains(status)
        || !Set.of("newest", "amount", "title").contains(sort))
      throw new Problem(400, "INVALID_INPUT");
    var p = new ArrayList<Object>();
    String w = scope(p);
    if (!search.isBlank()) {
      p.add("%" + search.toLowerCase(Locale.ROOT) + "%");
      w += " and (lower(c.title) like ?" + p.size() + " or lower(c.number) like ?" + p.size() + ")";
    }
    for (var f :
        List.of(
            new String[] {"status", status},
            new String[] {"kind", kind},
            new String[] {"category", category})) {
      if (!f[1].isBlank()) {
        p.add(f[1]);
        w += " and c." + f[0] + "=?" + p.size();
      }
    }
    var count = db.jpql(Long.class, "select count(c) from ExpenseCase c where " + w);
    var q =
        db.jpql(
            ExpenseCase.class,
            "from ExpenseCase c where "
                + w
                + " order by "
                + switch (sort) {
                  case "amount" -> "c.amountCents desc,c.id desc";
                  case "title" -> "c.title,c.id";
                  default -> "c.id desc";
                });
    for (int i = 0; i < p.size(); i++) {
      count.setParameter(i + 1, p.get(i));
      q.setParameter(i + 1, p.get(i));
    }
    return Map.of(
        "items",
        q.setFirstResult(page * size).setMaxResults(size).getResultList(),
        "total",
        count.getSingleResult());
  }

  /** 单据详情含凭证元数据、付款原记录和不可变送审快照，字节只能由受控下载获取。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(Long id) {
    access.require("expense.read");
    var c = read(id);
    var m = new LinkedHashMap<String, Object>();
    m.put("record", c);
    m.put("lines", lines(id));
    m.put("evidence", evidenceMeta(id));
    m.put("events", events(id));
    m.put("payments", payments(id));
    m.put("budget", budgetFor(c));
    m.put("actions", actions(c));
    m.put(
        "application", c.applicationId == null ? null : db.get(ExpenseCase.class, c.applicationId));
    m.put("claim", linked(id));
    return m;
  }

  /** 状态命令、预算变化与审计在同一事务；请求键精确去重。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object act(Long id, String action, Command v) {
    var c = lock(id);
    var fingerprint = ExpensePolicy.hash(action + "\n" + json.writeValueAsString(v));
    if (v.requestKey() == null || !v.requestKey().matches("[A-Za-z0-9_-]{8,80}"))
      throw new Problem(400, "INVALID_REQUEST_KEY");
    var prior =
        db.query(
            CommandStamp.class,
            "from CommandStamp where caseId=?1 and actor=?2 and requestKey=?3",
            id,
            access.current().username,
            v.requestKey());
    // A manager return changes the stage. Exact retries still check the actor's current role.
    if (!prior.isEmpty() && action.equals("reject")) {
      var actor = access.current();
      boolean manager = can("expense.manager") && Objects.equals(c.managerId, actor.id);
      boolean finance =
          can("expense.finance")
              && access.role().scope.equals("ALL")
              && Objects.equals(c.financeId, actor.id);
      if (actor.id.equals(c.applicantId) || !(manager || finance))
        throw new Problem(403, "NOT_APPROVER");
    } else authorize(c, action);
    if (!prior.isEmpty()) {
      if (!prior.getFirst().fingerprint.equals(fingerprint))
        throw new Problem(409, "IDEMPOTENCY_CONFLICT");
      return detail(id);
    }
    ExpensePolicy.version(c, v.version());
    switch (action) {
      case "submit" -> {
        ExpensePolicy.state(c, "DRAFT", "REJECTED");
        validateSubmit(c);
        for (var l : lines(id)) l.activeReceiptKey = receiptKey(l);
        for (var e : evidence(id)) e.frozen = true;
        c.submitted = true;
        c.status = "MANAGER";
        event(c, "SUBMIT", "", true);
      }
      case "recall" -> {
        ExpensePolicy.state(c, "MANAGER");
        c.status = "DRAFT";
        event(c, "RECALL", note(v), false);
      }
      case "manager-approve" -> {
        ExpensePolicy.state(c, "MANAGER");
        validateApprovers(c);
        c.status = "FINANCE";
        event(c, "MANAGER_APPROVE", note(v), false);
      }
      case "finance-approve" -> {
        ExpensePolicy.state(c, "FINANCE");
        validateApprovers(c);
        approveBudget(c, v);
        c.status = "APPROVED";
        event(c, "FINANCE_APPROVE", note(v), false);
      }
      case "reject" -> {
        ExpensePolicy.state(c, "MANAGER", "FINANCE");
        c.status = "REJECTED";
        event(c, "REJECT", note(v), false);
      }
      case "cancel" -> {
        ExpensePolicy.state(c, "DRAFT", "REJECTED", "MANAGER", "FINANCE", "APPROVED");
        if (c.kind.equals("APPLICATION") && linked(id) != null)
          throw new Problem(409, "LINKED_CLAIM_EXISTS");
        if (c.status.equals("APPROVED")) {
          var b = requiredBudget(c);
          if (c.kind.equals("APPLICATION")) {
            b.reservedCents -= c.reservedCents;
            c.reservedCents = 0;
          } else b.committedCents -= c.amountCents;
        }
        for (var l : lines(id)) l.activeReceiptKey = null;
        c.status = "CANCELLED";
        event(c, "CANCEL", note(v), false);
      }
      case "close" -> {
        ExpensePolicy.state(c, "APPROVED");
        if (!c.kind.equals("APPLICATION")) throw new Problem(409, "INVALID_STATE");
        var claim = linked(id);
        if (claim != null && !Set.of("PAID", "CANCELLED").contains(claim.status))
          throw new Problem(409, "LINKED_CLAIM_EXISTS");
        var b = requiredBudget(c);
        b.reservedCents -= c.reservedCents;
        c.reservedCents = 0;
        c.status = "CLOSED";
        event(c, "CLOSE", note(v), false);
      }
      case "pay" -> {
        ExpensePolicy.state(c, "APPROVED", "PART_PAID");
        if (!c.kind.equals("CLAIM")) throw new Problem(409, "INVALID_STATE");
        long amount = ExpensePolicy.cents(v.amount(), false);
        if (amount > c.amountCents - c.paidCents) throw new Problem(409, "OVERPAYMENT");
        dictionary("payment", v.method());
        validPaymentDate(v.paymentDate());
        var pay = new PaymentRecord();
        pay.caseId = id;
        pay.amountCents = amount;
        pay.reference = text(v.reference(), 120);
        pay.method = v.method();
        pay.actor = access.current().username;
        pay.paymentDate = v.paymentDate();
        pay.createdAt = clock.instant();
        pay.reversalReason = "";
        pay.reversedBy = "";
        db.save(pay);
        c.paidCents += amount;
        paymentState(c);
        event(c, "PAY", note(v), false);
      }
      case "reverse" -> {
        ExpensePolicy.state(c, "PART_PAID", "PAID");
        var pay = db.get(PaymentRecord.class, v.paymentId());
        if (!pay.caseId.equals(id) || pay.reversedAt != null)
          throw new Problem(409, "INVALID_PAYMENT");
        pay.reversedAt = clock.instant();
        pay.reversalReason = note(v);
        pay.reversedBy = access.current().username;
        c.paidCents -= pay.amountCents;
        paymentState(c);
        event(c, "REVERSE", pay.reference + ": " + pay.reversalReason, false);
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    var stamp = new CommandStamp();
    stamp.caseId = id;
    stamp.actor = access.current().username;
    stamp.requestKey = v.requestKey();
    stamp.fingerprint = fingerprint;
    db.save(stamp);
    db.flush();
    return detail(id);
  }

  /**
   * 上传5MiB以内真实PNG/JPEG/PDF文件，按签名和扩展名联合校验，原文件名不保存。官网 https://www.zhuatech.cn/；微信 zhuatech /
   * zhuatech2。
   */
  public Object upload(Long id, Long version, MultipartFile file) {
    access.require("expense.write");
    var c = lock(id);
    author(c);
    ExpensePolicy.state(c, "DRAFT", "REJECTED");
    ExpensePolicy.version(c, version);
    if (!c.kind.equals("CLAIM")
        || file.isEmpty()
        || file.getSize() > 5242880
        || evidence(id).size() >= 20) throw new Problem(400, "INVALID_FILE");
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (java.io.IOException e) {
      throw new Problem(400, "INVALID_FILE");
    }
    String filename =
        Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
    String ext, ctype;
    if (bytes.length > 8
        && Arrays.equals(
            Arrays.copyOf(bytes, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10})
        && filename.endsWith(".png")) {
      ext = "png";
      ctype = "image/png";
    } else if (bytes.length > 4
        && bytes[0] == (byte) 255
        && bytes[1] == (byte) 216
        && bytes[2] == (byte) 255
        && (filename.endsWith(".jpg") || filename.endsWith(".jpeg"))) {
      ext = "jpg";
      ctype = "image/jpeg";
    } else if (bytes.length > 8
        && new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")
        && filename.endsWith(".pdf")) {
      ext = "pdf";
      ctype = "application/pdf";
    } else throw new Problem(400, "INVALID_FILE");
    var e = new Evidence();
    e.caseId = id;
    e.bytes = bytes;
    e.size = bytes.length;
    e.hash = ExpensePolicy.hash(bytes);
    e.extension = ext;
    e.contentType = ctype;
    e.createdAt = clock.instant();
    db.save(e);
    event(c, "UPLOAD_EVIDENCE", "", false);
    db.flush();
    return detail(id);
  }

  /** 凭证下载逐次校验单据权限，强制附件响应且不使用输入文件路径。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Evidence download(Long id, Long eid) {
    access.require("expense.read");
    read(id);
    var e = db.get(Evidence.class, eid);
    if (!e.caseId.equals(id)) throw new Problem(404, "NOT_FOUND");
    return e;
  }

  /** 未送审凭证可删除；已用于送审的凭证永久保留以支撑快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object removeEvidence(Long id, Long eid, Long version) {
    access.require("expense.write");
    var c = lock(id);
    author(c);
    ExpensePolicy.state(c, "DRAFT", "REJECTED");
    ExpensePolicy.version(c, version);
    var e = db.get(Evidence.class, eid);
    if (!e.caseId.equals(id)) throw new Problem(404, "NOT_FOUND");
    if (e.frozen) throw new Problem(409, "HISTORY_PROTECTED");
    db.delete(e);
    event(c, "DELETE_EVIDENCE", "", false);
    db.flush();
    return detail(id);
  }

  /** 预算创建和调整，不能低于已有占用，部门行锁与业务写入共享锁顺序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveBudget(Long id, BudgetInput v) {
    access.require("expense.budget");
    access.department(v.departmentId());
    db.lock(Department.class, v.departmentId());
    dictionary("category", v.category());
    String month = ExpensePolicy.month(v.budgetMonth());
    long limit = ExpensePolicy.cents(v.limit(), true);
    var b = id == null ? new Budget() : db.get(Budget.class, id);
    if (id != null) {
      if (!b.departmentId.equals(v.departmentId())
          || !b.category.equals(v.category())
          || !b.budgetMonth.equals(month)) throw new Problem(409, "BUDGET_KEY_IMMUTABLE");
      if (v.version() == null || !v.version().equals(b.version))
        throw new Problem(409, "STALE_VERSION");
      if (limit < b.reservedCents + b.committedCents)
        throw new Problem(409, "BUDGET_BELOW_COMMITTED");
    } else {
      b.departmentId = v.departmentId();
      b.category = v.category();
      b.budgetMonth = month;
      db.save(b);
    }
    b.limitCents = limit;
    access.audit("BUDGET_UPDATE", id == null ? "NEW" : id, v.departmentId());
    db.flush();
    return budgets();
  }

  /** 查看数据范围内预算；员工只查看本人部门的汇总额度。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object budgets() {
    access.require("expense.read");
    return db.all(Budget.class).stream().filter(b -> access.visible(b.departmentId)).toList();
  }

  /** 我的申请和本人待审、付款登记待办，读取受数据范围限制。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object workbench() {
    access.require("expense.read");
    var me = access.current();
    var rows = visibleCases();
    return Map.of(
        "mine",
        rows.stream()
            .filter(
                c ->
                    c.applicantId.equals(me.id)
                        && !Set.of("PAID", "CLOSED", "CANCELLED").contains(c.status))
            .toList(),
        "approvals",
        rows.stream()
            .filter(
                c ->
                    c.status.equals("MANAGER")
                            && Objects.equals(c.managerId, me.id)
                            && can("expense.manager")
                        || c.status.equals("FINANCE")
                            && Objects.equals(c.financeId, me.id)
                            && can("expense.finance"))
            .toList(),
        "payments",
        rows.stream()
            .filter(
                c ->
                    c.kind.equals("CLAIM")
                        && Set.of("APPROVED", "PART_PAID").contains(c.status)
                        && can("expense.pay")
                        && !c.applicantId.equals(me.id))
            .toList());
  }

  /** 范围内申请和费用统计；批准费用与付款登记严格分开。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var rows = visibleCases();
    var claims =
        rows.stream()
            .filter(
                c ->
                    c.kind.equals("CLAIM")
                        && Set.of("APPROVED", "PART_PAID", "PAID").contains(c.status))
            .toList();
    var counts = new TreeMap<String, Long>();
    for (var c : rows) counts.merge(c.status, 1L, Long::sum);
    return Map.of(
        "count",
        rows.size(),
        "overdue",
        rows.stream()
            .filter(
                c ->
                    Set.of("MANAGER", "FINANCE").contains(c.status)
                        && c.updatedAt
                            .plusSeconds(
                                86400L
                                    * Integer.parseInt(
                                        db.query(
                                                SystemSetting.class,
                                                "from SystemSetting where code='approvalReminderDays'")
                                            .getFirst()
                                            .value))
                            .isBefore(clock.instant()))
            .count(),
        "states",
        counts,
        "approvedCents",
        claims.stream().mapToLong(c -> c.amountCents).sum(),
        "paidCents",
        claims.stream().mapToLong(c -> c.paidCents).sum(),
        "pendingCents",
        claims.stream().mapToLong(c -> c.amountCents - c.paidCents).sum(),
        "budgets",
        budgets());
  }

  /** 仅返回范围内不含凭证载荷的操作审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream()
        .filter(
            e ->
                access.role().scope.equals("ALL")
                    || access.role().scope.equals("ASSIGNED")
                        && e.actor.equals(access.current().username)
                    || access.role().scope.equals("DEPARTMENT")
                        && e.departmentId.equals(access.current().departmentId))
        .sorted(Comparator.comparing((AuditEvent e) -> e.id).reversed())
        .toList();
  }

  /** 授权 JSON 快照导出，不混入联系方式或原始附件字节。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String export(Long id) {
    access.require("export");
    return json.writeValueAsString(detail(id));
  }

  private void fill(ExpenseCase c, Draft v) {
    c.title = text(v.title(), 200);
    c.purpose = text(v.purpose(), 3000);
    c.category = text(v.category(), 60);
    dictionary("category", c.category);
    c.budgetMonth = ExpensePolicy.month(v.budgetMonth());
    c.managerId = v.managerId();
    c.financeId = v.financeId();
    c.applicationId = v.applicationId();
    if (c.applicationId != null) {
      if (!c.kind.equals("CLAIM") || c.applicationId.equals(c.id))
        throw new Problem(400, "INVALID_APPLICATION");
      var a = db.get(ExpenseCase.class, c.applicationId);
      if (!a.kind.equals("APPLICATION")
          || !a.status.equals("APPROVED")
          || !a.applicantId.equals(c.applicantId)
          || !a.departmentId.equals(c.departmentId)
          || !a.category.equals(c.category)
          || !a.budgetMonth.equals(c.budgetMonth)) throw new Problem(400, "INVALID_APPLICATION");
    }
    // Receipt keys on a returned claim remain reserved until save validates and replaces the lines.
    boolean keepKeys = c.submitted;
    for (var old : lines(c.id)) db.delete(old);
    if (c.kind.equals("APPLICATION")) {
      if (v.lines() != null && !v.lines().isEmpty()) throw new Problem(400, "INVALID_INPUT");
      c.amountCents = ExpensePolicy.cents(v.amount(), false);
    } else {
      if (v.lines() == null || v.lines().isEmpty() || v.lines().size() > 20)
        throw new Problem(400, "LINES_REQUIRED");
      long amount = 0;
      var keys = new HashSet<String>();
      for (var input : v.lines()) {
        if (input.expenseDate() == null
            || input.expenseDate().isAfter(today())
            || !YearMonth.from(input.expenseDate()).toString().equals(c.budgetMonth))
          throw new Problem(400, "INVALID_EXPENSE_DATE");
        var l = new ExpenseLine();
        l.caseId = c.id;
        l.expenseDate = input.expenseDate();
        l.category = c.category;
        l.description = text(input.description(), 500);
        l.amountCents = ExpensePolicy.cents(input.amount(), false);
        l.issuer = text(input.issuer(), 120).toUpperCase(Locale.ROOT);
        l.reference = text(input.reference(), 120).toUpperCase(Locale.ROOT);
        var key = receiptKey(l);
        if (!keys.add(key)) throw new Problem(409, "DUPLICATE_RECEIPT");
        if (keepKeys) l.activeReceiptKey = key;
        amount = ExpensePolicy.sum(amount, l.amountCents);
        db.save(l);
      }
      c.amountCents = amount;
    }
    if (c.applicationId != null
        && c.amountCents > db.get(ExpenseCase.class, c.applicationId).amountCents)
      throw new Problem(400, "APPLICATION_LIMIT");
  }

  private void validateSubmit(ExpenseCase c) {
    validateApprovers(c);
    requiredBudget(c);
    if (c.kind.equals("CLAIM") && evidence(c.id).isEmpty())
      throw new Problem(400, "EVIDENCE_REQUIRED");
    if (c.applicationId != null) {
      var a = db.get(ExpenseCase.class, c.applicationId);
      if (!a.status.equals("APPROVED") || a.reservedCents < c.amountCents)
        throw new Problem(409, "INVALID_APPLICATION");
    }
  }

  private void validateApprovers(ExpenseCase c) {
    ExpensePolicy.independent(c.applicantId, c.managerId, c.financeId);
    var m = db.get(Account.class, c.managerId);
    var f = db.get(Account.class, c.financeId);
    if (!m.enabled
        || !m.departmentId.equals(c.departmentId)
        || !role(m).permissions.contains("expense.manager")
        || !f.enabled
        || !role(f).permissions.contains("expense.finance")
        || !role(f).scope.equals("ALL")) throw new Problem(400, "INVALID_ASSIGNEE");
  }

  private void approveBudget(ExpenseCase c, Command v) {
    var b = requiredBudget(c);
    long projected =
        ExpensePolicy.sum(ExpensePolicy.sum(b.reservedCents, b.committedCents), c.amountCents);
    ExpenseCase a = null;
    if (c.applicationId != null) {
      a = db.get(ExpenseCase.class, c.applicationId);
      if (!a.status.equals("APPROVED") || a.reservedCents < c.amountCents)
        throw new Problem(409, "INVALID_APPLICATION");
      projected -= a.reservedCents;
    }
    if (projected > b.limitCents && !Boolean.TRUE.equals(v.allowOverBudget()))
      throw new Problem(409, "OVER_BUDGET_CONFIRMATION_REQUIRED");
    if (c.kind.equals("APPLICATION")) {
      b.reservedCents = ExpensePolicy.sum(b.reservedCents, c.amountCents);
      c.reservedCents = c.amountCents;
    } else {
      if (a != null) {
        b.reservedCents -= a.reservedCents;
        a.reservedCents = 0;
        event(a, "CLAIM_COMMITTED", c.number, false);
      }
      b.committedCents = ExpensePolicy.sum(b.committedCents, c.amountCents);
    }
  }

  private void validPaymentDate(LocalDate date) {
    if (date == null || date.isAfter(today())) throw new Problem(400, "INVALID_PAYMENT_DATE");
  }

  private void paymentState(ExpenseCase c) {
    c.status = c.paidCents == c.amountCents ? "PAID" : c.paidCents == 0 ? "APPROVED" : "PART_PAID";
  }

  private void dictionary(String type, String code) {
    if (db.query(
            DictionaryEntry.class, "from DictionaryEntry where type=?1 and code=?2", type, code)
        .isEmpty()) throw new Problem(400, "INVALID_DICTIONARY");
  }

  private String receiptKey(ExpenseLine l) {
    return ExpensePolicy.hash(l.issuer + "\n" + l.reference);
  }

  private Budget budgetFor(ExpenseCase c) {
    var rows =
        db.query(
            Budget.class,
            "from Budget where departmentId=?1 and category=?2 and budgetMonth=?3",
            c.departmentId,
            c.category,
            c.budgetMonth);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private Budget requiredBudget(ExpenseCase c) {
    var b = budgetFor(c);
    if (b == null) throw new Problem(409, "BUDGET_REQUIRED");
    return b;
  }

  private ExpenseCase linked(Long id) {
    var list = db.query(ExpenseCase.class, "from ExpenseCase where applicationId=?1", id);
    return list.isEmpty() ? null : list.getFirst();
  }

  private List<ExpenseLine> lines(Long id) {
    return db.query(ExpenseLine.class, "from ExpenseLine where caseId=?1 order by id", id);
  }

  private List<Evidence> evidence(Long id) {
    return db.query(Evidence.class, "from Evidence where caseId=?1 order by id", id);
  }

  private List<Map<String, Object>> evidenceMeta(Long id) {
    return evidence(id).stream()
        .map(
            e ->
                Map.<String, Object>of(
                    "id",
                    e.id,
                    "contentType",
                    e.contentType,
                    "extension",
                    e.extension,
                    "hash",
                    e.hash,
                    "size",
                    e.size,
                    "frozen",
                    e.frozen,
                    "createdAt",
                    e.createdAt))
        .toList();
  }

  private List<ExpenseEvent> events(Long id) {
    return db.query(ExpenseEvent.class, "from ExpenseEvent where caseId=?1 order by id desc", id);
  }

  private List<PaymentRecord> payments(Long id) {
    return db.query(PaymentRecord.class, "from PaymentRecord where caseId=?1 order by id", id);
  }

  private AccessRole role(Account a) {
    return db.get(AccessRole.class, a.roleId);
  }

  private boolean can(String p) {
    return access.role().permissions.contains(p);
  }

  private boolean visible(ExpenseCase c) {
    if (access.role().scope.equals("ALL")) return true;
    if (!access.current().departmentId.equals(c.departmentId)) return false;
    if (access.role().scope.equals("DEPARTMENT")) return true;
    var id = access.current().id;
    return c.applicantId.equals(id)
        || Objects.equals(c.managerId, id)
        || Objects.equals(c.financeId, id);
  }

  private ExpenseCase read(Long id) {
    var c = db.get(ExpenseCase.class, id);
    if (!visible(c)) throw new Problem(403, "OUT_OF_SCOPE");
    return c;
  }

  private ExpenseCase lock(Long id) {
    access.require("expense.read");
    var c = read(id);
    db.lock(Department.class, c.departmentId);
    db.refresh(c);
    return c;
  }

  private void author(ExpenseCase c) {
    if (!c.applicantId.equals(access.current().id)) throw new Problem(403, "NOT_APPLICANT");
  }

  private void authorize(ExpenseCase c, String action) {
    switch (action) {
      case "submit", "recall", "cancel", "close" -> {
        access.require("expense.write");
        author(c);
      }
      case "manager-approve" -> {
        access.require("expense.manager");
        if (!Objects.equals(c.managerId, access.current().id)
            || c.applicantId.equals(access.current().id)) throw new Problem(403, "NOT_APPROVER");
      }
      case "finance-approve" -> {
        access.require("expense.finance");
        if (!access.role().scope.equals("ALL")
            || !Objects.equals(c.financeId, access.current().id)
            || c.applicantId.equals(access.current().id)) throw new Problem(403, "NOT_APPROVER");
      }
      case "reject" -> {
        String permission = c.status.equals("MANAGER") ? "expense.manager" : "expense.finance";
        access.require(permission);
        var id = c.status.equals("MANAGER") ? c.managerId : c.financeId;
        if (!Objects.equals(id, access.current().id) || c.applicantId.equals(access.current().id))
          throw new Problem(403, "NOT_APPROVER");
      }
      case "pay", "reverse" -> {
        access.require("expense.pay");
        if (!access.role().scope.equals("ALL") || c.applicantId.equals(access.current().id))
          throw new Problem(403, "NOT_PAYER");
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
  }

  private List<String> actions(ExpenseCase c) {
    var actions = new ArrayList<String>();
    for (var action :
        List.of(
            "submit",
            "recall",
            "manager-approve",
            "finance-approve",
            "reject",
            "cancel",
            "close",
            "pay",
            "reverse")) {
      boolean state =
          switch (action) {
            case "submit" -> Set.of("DRAFT", "REJECTED").contains(c.status);
            case "recall", "manager-approve" -> c.status.equals("MANAGER");
            case "finance-approve" -> c.status.equals("FINANCE");
            case "reject" -> Set.of("MANAGER", "FINANCE").contains(c.status);
            case "cancel" ->
                Set.of("DRAFT", "REJECTED", "MANAGER", "FINANCE", "APPROVED").contains(c.status);
            case "close" -> c.kind.equals("APPLICATION") && c.status.equals("APPROVED");
            case "pay" ->
                c.kind.equals("CLAIM") && Set.of("APPROVED", "PART_PAID").contains(c.status);
            default -> Set.of("PART_PAID", "PAID").contains(c.status);
          };
      if (state) {
        try {
          authorize(c, action);
          actions.add(action);
        } catch (Problem ignored) {
        }
      }
    }
    return actions;
  }

  private List<ExpenseCase> visibleCases() {
    var p = new ArrayList<Object>();
    var q =
        db.jpql(ExpenseCase.class, "from ExpenseCase c where " + scope(p) + " order by c.id desc")
            .setMaxResults(10001);
    for (int i = 0; i < p.size(); i++) q.setParameter(i + 1, p.get(i));
    var rows = q.getResultList();
    if (rows.size() > 10000) throw new Problem(400, "REPORT_LIMIT");
    return rows;
  }

  private String scope(List<Object> p) {
    if (access.role().scope.equals("ALL")) return "1=1";
    p.add(access.current().departmentId);
    String w = "c.departmentId=?1";
    if (access.role().scope.equals("ASSIGNED")) {
      p.add(access.current().id);
      w += " and (c.applicantId=?2 or c.managerId=?2 or c.financeId=?2)";
    }
    return w;
  }

  private void event(ExpenseCase c, String action, String note, boolean snapshot) {
    c.changeCount++;
    c.updatedAt = clock.instant();
    var e = new ExpenseEvent();
    e.caseId = c.id;
    e.actor = access.current().username;
    e.action = action;
    e.note = note;
    e.createdAt = clock.instant();
    e.snapshot =
        snapshot
            ? json.writeValueAsString(
                Map.of("record", c, "lines", lines(c.id), "evidence", evidenceMeta(c.id)))
            : "";
    db.save(e);
    access.audit(action, c.id, c.departmentId);
  }

  private LocalDate today() {
    var settings = db.query(SystemSetting.class, "from SystemSetting where code='timezone'");
    return LocalDate.now(
        clock.withZone(
            ZoneId.of(settings.isEmpty() ? "Asia/Shanghai" : settings.getFirst().value)));
  }

  private String note(Command v) {
    return text(v.note(), 1000);
  }

  private String text(String s, int max) {
    return AdminService.text(s, max);
  }
}
