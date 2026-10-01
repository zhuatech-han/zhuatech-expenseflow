// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.expenseflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 真实 HTTP、会话、MySQL模式迁移、权限、预算、付款与并发验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
class ExpenseIntegrationTest {
  static final String password = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("expenseflow.admin-password", () -> password);
  }

  @Autowired MockMvc mvc;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, employee, manager, finance, payer, stranger;
  long dept, employeeId, managerId, financeId, payerId, id;
  JsonNode d;
  String employeeName, reference;

  LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Shanghai"));
  }

  String month() {
    return YearMonth.from(today()).toString();
  }

  @BeforeEach
  void setup() throws Exception {
    admin = login("admin", password);
    String s = UUID.randomUUID().toString().substring(0, 8);
    reference = "RECEIPT-" + s;
    dept =
        ok(admin, "POST", "/admin/departments", Map.of("name", "费用验收（虚构）-" + s))
            .path("id")
            .asLong();
    Map<String, Long> roles = new HashMap<>();
    for (var r : ok(admin, "GET", "/admin/roles", null))
      roles.put(r.path("name").asString(), r.path("id").asLong());
    employeeName = "employee-" + s;
    employeeId = user(employeeName, roles.get("员工"), dept);
    managerId = user("manager-" + s, roles.get("部门主管"), dept);
    financeId = user("finance-" + s, roles.get("财务复核"), 1L);
    payerId = user("payer-" + s, roles.get("付款登记"), 1L);
    user("stranger-" + s, roles.get("员工"), dept);
    employee = login(employeeName, password);
    manager = login("manager-" + s, password);
    finance = login("finance-" + s, password);
    payer = login("payer-" + s, password);
    stranger = login("stranger-" + s, password);
    ok(
        manager,
        "POST",
        "/budgets",
        Map.of(
            "departmentId",
            dept,
            "category",
            "OFFICE",
            "budgetMonth",
            month(),
            "limit",
            "1000.00"));
    d = ok(employee, "POST", "/cases", draft("APPLICATION", null, "100.01"));
    id = d.path("record").path("id").asLong();
  }

  long user(String n, long role, long dep) throws Exception {
    return ok(
            admin,
            "POST",
            "/admin/users",
            Map.of(
                "username",
                n,
                "displayName",
                n,
                "password",
                password,
                "roleId",
                role,
                "departmentId",
                dep,
                "enabled",
                true))
        .path("id")
        .asLong();
  }

  Map<String, Object> draft(String kind, Long app, String amount) {
    var m = new LinkedHashMap<String, Object>();
    m.put("kind", kind);
    m.put("title", "办公费用验收（虚构测试）");
    m.put("category", "OFFICE");
    m.put("budgetMonth", month());
    m.put("purpose", "仅用于隔离环境业务验收");
    m.put("amount", amount);
    m.put("managerId", managerId);
    m.put("financeId", financeId);
    m.put("applicationId", app);
    if (kind.equals("CLAIM"))
      m.put(
          "lines",
          List.of(
              Map.of(
                  "expenseDate",
                  today().toString(),
                  "description",
                  "虚构凭证验收",
                  "amount",
                  amount,
                  "issuer",
                  "TEST ISSUER",
                  "reference",
                  reference)));
    return m;
  }

  long version() {
    return d.path("record").path("version").asLong();
  }

  String path() {
    return "/cases/" + id;
  }

  Map<String, Object> cmd() {
    return new LinkedHashMap<>(
        Map.of("version", version(), "requestKey", UUID.randomUUID().toString(), "note", "虚构验收意见"));
  }

  JsonNode action(MockHttpSession who, String action) throws Exception {
    d = ok(who, "POST", path() + "/commands/" + action, cmd());
    return d;
  }

  void approve() throws Exception {
    action(employee, "submit");
    action(manager, "manager-approve");
    action(finance, "finance-approve");
  }

  void claim(Long application, String amount) throws Exception {
    d = ok(employee, "POST", "/cases", draft("CLAIM", application, amount));
    id = d.path("record").path("id").asLong();
    upload("test.pdf", pdf(), 200);
  }

  byte[] pdf() {
    return "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF"
        .getBytes(StandardCharsets.US_ASCII);
  }

  void upload(String filename, byte[] bytes, int expected) throws Exception {
    var r =
        mvc.perform(
                multipart("/api" + path() + "/evidence")
                    .file(
                        new MockMultipartFile("file", filename, "application/octet-stream", bytes))
                    .param("version", String.valueOf(version()))
                    .session(employee)
                    .with(csrf()))
            .andReturn();
    assertEquals(expected, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    if (expected == 200) d = json.readTree(r.getResponse().getContentAsString());
  }

  MvcResult request(MockHttpSession who, String method, String path, Object body, boolean csrfToken)
      throws Exception {
    var r =
        switch (method) {
          case "GET" -> get("/api" + path);
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          default -> delete("/api" + path);
        };
    if (who != null) r.session(who);
    if (csrfToken) r.with(csrf());
    if (body != null) r.contentType("application/json").content(json.writeValueAsString(body));
    return mvc.perform(r).andReturn();
  }

  JsonNode ok(MockHttpSession who, String method, String path, Object body) throws Exception {
    var r = request(who, method, path, body, true);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void denied(MockHttpSession who, String method, String path, Object body, int status)
      throws Exception {
    var r = request(who, method, path, body, true);
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
  }

  MockHttpSession login(String name, String pwd) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("username", name, "password", pwd))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  JsonNode budget() throws Exception {
    for (var b : ok(finance, "GET", "/budgets", null))
      if (b.path("departmentId").asLong() == dept && b.path("category").asString().equals("OFFICE"))
        return b;
    throw new AssertionError();
  }

  @Test
  void completeApplicationClaimPartialPaymentAndReversal() throws Exception {
    approve();
    long app = id;
    assertEquals(10001, budget().path("reservedCents").asLong());
    claim(app, "80.01");
    approve();
    assertEquals(0, budget().path("reservedCents").asLong());
    assertEquals(8001, budget().path("committedCents").asLong());
    var pay = cmd();
    pay.put("amount", "30.00");
    pay.put("reference", "PAY-A");
    pay.put("method", "BANK");
    pay.put("paymentDate", today().toString());
    d = ok(payer, "POST", path() + "/commands/pay", pay);
    assertEquals("PART_PAID", d.path("record").path("status").asString());
    var second = cmd();
    second.put("amount", "50.01");
    second.put("reference", "PAY-B");
    second.put("method", "BANK");
    second.put("paymentDate", today().toString());
    d = ok(payer, "POST", path() + "/commands/pay", second);
    assertEquals("PAID", d.path("record").path("status").asString());
    var reverse = cmd();
    reverse.put("paymentId", d.path("payments").get(0).path("id").asLong());
    d = ok(payer, "POST", path() + "/commands/reverse", reverse);
    assertEquals(5001, d.path("record").path("paidCents").asLong());
    assertFalse(d.path("payments").get(0).path("reversedAt").isNull());
    assertEquals(8001, budget().path("committedCents").asLong());
    assertEquals(2, d.path("payments").size());
  }

  @Test
  void departmentAndPersonalScope() throws Exception {
    denied(stranger, "GET", path(), null, 403);
    assertEquals(0, ok(stranger, "GET", "/cases", null).path("total").asLong());
    assertTrue(ok(manager, "GET", path(), null).has("record"));
    denied(stranger, "GET", path() + "/report.json", null, 403);
  }

  @Test
  void independentApprovalRequired() throws Exception {
    var m = draft("APPLICATION", null, "20");
    m.put("managerId", employeeId);
    m.put("version", version());
    d = ok(employee, "PUT", path(), m);
    denied(employee, "POST", path() + "/commands/submit", cmd(), 400);
  }

  @Test
  void assignedApproversOnly() throws Exception {
    action(employee, "submit");
    denied(admin, "POST", path() + "/commands/manager-approve", cmd(), 403);
    denied(finance, "POST", path() + "/commands/manager-approve", cmd(), 403);
    denied(employee, "POST", path() + "/commands/manager-approve", cmd(), 403);
  }

  @Test
  void staleWriteCannotOverwrite() throws Exception {
    var m = draft("APPLICATION", null, "20");
    m.put("version", version());
    d = ok(employee, "PUT", path(), m);
    denied(employee, "PUT", path(), m, 409);
  }

  @Test
  void rejectedSnapshotPreserved() throws Exception {
    action(employee, "submit");
    String frozen = d.path("events").get(0).path("snapshot").asString();
    var returned = cmd();
    d = ok(manager, "POST", path() + "/commands/reject", returned);
    long eventCount = d.path("events").size();
    d = ok(manager, "POST", path() + "/commands/reject", returned);
    assertEquals(eventCount, d.path("events").size());
    returned.put("note", "changed reason");
    denied(manager, "POST", path() + "/commands/reject", returned, 409);
    var v = draft("APPLICATION", null, "22.01");
    v.put("version", version());
    d = ok(employee, "PUT", path(), v);
    action(employee, "submit");
    var snapshots = new ArrayList<String>();
    for (var e : d.path("events"))
      if (e.path("action").asString().equals("SUBMIT"))
        snapshots.add(e.path("snapshot").asString());
    assertEquals(2, snapshots.size());
    assertTrue(snapshots.contains(frozen));
    assertNotEquals(snapshots.get(0), snapshots.get(1));
  }

  @Test
  void budgetRequiredBeforeSubmit() throws Exception {
    var m = draft("APPLICATION", null, "20");
    m.put("version", version());
    m.put("category", "TRAVEL");
    d = ok(employee, "PUT", path(), m);
    denied(employee, "POST", path() + "/commands/submit", cmd(), 409);
  }

  @Test
  void overBudgetRequiresExplicitConfirmedReason() throws Exception {
    var m = draft("APPLICATION", null, "1100");
    m.put("version", version());
    d = ok(employee, "PUT", path(), m);
    action(employee, "submit");
    action(manager, "manager-approve");
    denied(finance, "POST", path() + "/commands/finance-approve", cmd(), 409);
    var c = cmd();
    c.put("allowOverBudget", true);
    c.put("note", "");
    denied(finance, "POST", path() + "/commands/finance-approve", c, 400);
    c.put("note", "验收批准超额（虚构）");
    d = ok(finance, "POST", path() + "/commands/finance-approve", c);
    assertEquals(110000, budget().path("reservedCents").asLong());
  }

  @Test
  void cancelAndCloseReleaseApplicationReservation() throws Exception {
    approve();
    action(employee, "close");
    assertEquals(0, budget().path("reservedCents").asLong());
    assertEquals("CLOSED", d.path("record").path("status").asString());
  }

  @Test
  void claimMustFitSamePersonCategoryMonthAndLimit() throws Exception {
    approve();
    long app = id;
    denied(employee, "POST", "/cases", draft("CLAIM", app, "200"), 400);
    denied(stranger, "POST", "/cases", draft("CLAIM", app, "80"), 400);
    claim(app, "80");
    long claimId = id;
    id = app;
    d = ok(employee, "GET", path(), null);
    denied(employee, "POST", path() + "/commands/close", cmd(), 409);
    id = claimId;
    d = ok(employee, "GET", path(), null);
  }

  @Test
  void directClaimAndCancellationAccounting() throws Exception {
    claim(null, "88.88");
    approve();
    assertEquals(8888, budget().path("committedCents").asLong());
    action(employee, "cancel");
    assertEquals(0, budget().path("committedCents").asLong());
  }

  @Test
  void evidenceRequiredAndFrozen() throws Exception {
    d = ok(employee, "POST", "/cases", draft("CLAIM", null, "10"));
    id = d.path("record").path("id").asLong();
    denied(employee, "POST", path() + "/commands/submit", cmd(), 400);
    upload("test.pdf", pdf(), 200);
    action(employee, "submit");
    long eid = d.path("evidence").get(0).path("id").asLong();
    action(manager, "reject");
    denied(employee, "DELETE", path() + "/evidence/" + eid + "?version=" + version(), null, 409);
    var r = request(stranger, "GET", path() + "/evidence/" + eid, null, true);
    assertEquals(403, r.getResponse().getStatus());
    r = request(employee, "GET", path() + "/evidence/" + eid, null, true);
    assertArrayEquals(pdf(), r.getResponse().getContentAsByteArray());
    assertEquals("no-store", r.getResponse().getHeader("Cache-Control"));
  }

  @Test
  void invalidFileHeadersAndOversizeRejected() throws Exception {
    claim(null, "10");
    upload("bad.svg", "<svg/>".getBytes(), 400);
    upload("bad.png", pdf(), 400);
    upload("big.pdf", new byte[5242881], 400);
  }

  @Test
  void duplicateReceiptAcrossSubmittedClaimsRejected() throws Exception {
    claim(null, "10");
    action(employee, "submit");
    long first = id;
    claim(null, "20");
    denied(employee, "POST", path() + "/commands/submit", cmd(), 409);
    assertEquals(
        "DRAFT", ok(employee, "GET", path(), null).path("record").path("status").asString());
    id = first;
    d = ok(employee, "GET", path(), null);
    action(employee, "cancel");
  }

  @Test
  void duplicateLinesWithinOneClaimRejected() throws Exception {
    var m = draft("CLAIM", null, "10");
    var line = ((List<?>) m.get("lines")).get(0);
    m.put("lines", List.of(line, line));
    denied(employee, "POST", "/cases", m, 409);
  }

  @Test
  void sameCommandRetryExactlyOnceAndChangedPayloadRejected() throws Exception {
    action(employee, "submit");
    var c = cmd();
    d = ok(manager, "POST", path() + "/commands/manager-approve", c);
    long count = d.path("events").size();
    d = ok(manager, "POST", path() + "/commands/manager-approve", c);
    assertEquals(count, d.path("events").size());
    c.put("note", "改变意见");
    denied(manager, "POST", path() + "/commands/manager-approve", c, 409);
  }

  @Test
  void parallelPaymentsCannotExceedApprovedAmount() throws Exception {
    claim(null, "10");
    approve();
    var a = cmd();
    a.put("amount", "10");
    a.put("reference", "CONCURRENT-A");
    a.put("method", "BANK");
    a.put("paymentDate", today().toString());
    var b = new HashMap<>(a);
    b.put("reference", "CONCURRENT-B");
    b.put("requestKey", UUID.randomUUID().toString());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var f1 =
          executor.submit(
              () ->
                  request(payer, "POST", path() + "/commands/pay", a, true)
                      .getResponse()
                      .getStatus());
      var f2 =
          executor.submit(
              () ->
                  request(payer, "POST", path() + "/commands/pay", b, true)
                      .getResponse()
                      .getStatus());
      assertEquals(
          Set.of(200, 409), Set.of(f1.get(20, TimeUnit.SECONDS), f2.get(20, TimeUnit.SECONDS)));
    }
    d = ok(employee, "GET", path(), null);
    assertEquals(1000, d.path("record").path("paidCents").asLong());
    assertEquals(1, d.path("payments").size());
  }

  @Test
  void noOverpaymentOrFuturePayment() throws Exception {
    claim(null, "10");
    approve();
    var p = cmd();
    p.put("amount", "10.01");
    p.put("reference", "BADPAY");
    p.put("method", "BANK");
    p.put("paymentDate", today().toString());
    denied(payer, "POST", path() + "/commands/pay", p, 409);
    p.put("amount", "5");
    p.put("paymentDate", today().plusDays(1).toString());
    denied(payer, "POST", path() + "/commands/pay", p, 400);
    denied(employee, "POST", path() + "/commands/pay", p, 403);
  }

  @Test
  void paidClaimCannotCancelOrEdit() throws Exception {
    claim(null, "10");
    approve();
    var p = cmd();
    p.put("amount", "5");
    p.put("reference", "PARTIAL");
    p.put("method", "CASH");
    p.put("paymentDate", today().toString());
    d = ok(payer, "POST", path() + "/commands/pay", p);
    denied(employee, "POST", path() + "/commands/cancel", cmd(), 409);
    var v = draft("CLAIM", null, "12");
    v.put("version", version());
    denied(employee, "PUT", path(), v, 409);
  }

  @Test
  void budgetCannotBeReducedBelowCommitted() throws Exception {
    approve();
    var b = budget();
    denied(
        manager,
        "PUT",
        "/budgets/" + b.path("id").asLong(),
        Map.of(
            "version",
            b.path("version").asLong(),
            "departmentId",
            dept,
            "category",
            "OFFICE",
            "budgetMonth",
            month(),
            "limit",
            "50"),
        409);
    denied(
        employee,
        "POST",
        "/budgets",
        Map.of("departmentId", dept, "category", "TRAVEL", "budgetMonth", month(), "limit", "100"),
        403);
  }

  @Test
  void draftDeletionAndHistoryProtection() throws Exception {
    long old = id;
    ok(employee, "DELETE", path() + "?version=" + version(), null);
    denied(employee, "GET", "/cases/" + old, null, 404);
    d = ok(employee, "POST", "/cases", draft("APPLICATION", null, "10"));
    id = d.path("record").path("id").asLong();
    action(employee, "submit");
    action(employee, "recall");
    denied(employee, "DELETE", path() + "?version=" + version(), null, 409);
  }

  @Test
  void csrfAdminAndPasswordHashProtection() throws Exception {
    assertEquals(
        403,
        request(employee, "POST", path() + "/commands/submit", cmd(), false)
            .getResponse()
            .getStatus());
    denied(employee, "GET", "/admin/users", null, 403);
    for (var u : ok(admin, "GET", "/admin/users", null)) {
      assertFalse(u.has("passwordHash"));
      if (u.path("username").asString().equals("admin"))
        denied(admin, "DELETE", "/admin/users/" + u.path("id").asLong(), null, 409);
    }
  }

  @Test
  void passwordChangeInvalidatesSession() throws Exception {
    ok(
        employee,
        "POST",
        "/auth/password",
        Map.of("oldPassword", password, "newPassword", "Bb8" + UUID.randomUUID()));
    assertTrue(employee.isInvalid());
  }

  @Test
  void searchPagingAndSafeExport() throws Exception {
    assertEquals(
        1,
        ok(
                employee,
                "GET",
                "/cases?search="
                    + d.path("record").path("number").asString()
                    + "&size=1&sort=amount",
                null)
            .path("total")
            .asLong());
    denied(employee, "GET", "/cases?sort=case%20when", null, 400);
    String report = ok(employee, "GET", path() + "/report.json", null).toString();
    assertFalse(report.contains("passwordHash"));
    assertFalse(report.contains("zhuatech2"));
    assertFalse(report.contains("payload"));
  }

  @Test
  void inactiveApproverRejected() throws Exception {
    var u = ok(admin, "GET", "/admin/users", null);
    for (var a : u)
      if (a.path("id").asLong() == managerId) {
        var m = json.convertValue(a, Map.class);
        m.put("enabled", false);
        ok(admin, "PUT", "/admin/users/" + managerId, m);
      }
    denied(employee, "POST", path() + "/commands/submit", cmd(), 400);
    denied(manager, "GET", path(), null, 401);
  }
}
