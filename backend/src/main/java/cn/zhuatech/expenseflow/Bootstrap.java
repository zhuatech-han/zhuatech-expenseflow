// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.expenseflow;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库初始化费用岗位和管理员，不植入报销或付款记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${expenseflow.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 仅首个空库创建强密码账号；重启不覆盖业务和密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var d = new Department();
    d.name = "总部";
    db.save(d);
    var names =
        Map.ofEntries(
            Map.entry("expense.read", "查看授权费用"),
            Map.entry("expense.write", "填报本人费用"),
            Map.entry("expense.manager", "主管审批"),
            Map.entry("expense.finance", "财务复核"),
            Map.entry("expense.pay", "付款与冲销登记"),
            Map.entry("expense.budget", "管理部门预算"),
            Map.entry("dashboard", "费用统计"),
            Map.entry("export", "导出授权单据"),
            Map.entry("audit", "操作审计"),
            Map.entry("admin", "系统管理"));
    for (var e : new TreeMap<>(names).entrySet()) {
      var x = new Permission();
      x.code = e.getKey();
      x.name = e.getValue();
      db.save(x);
    }
    role("管理员", "ALL", names.keySet());
    role("员工", "ASSIGNED", Set.of("expense.read", "expense.write", "dashboard", "export"));
    role(
        "部门主管",
        "DEPARTMENT",
        Set.of(
            "expense.read",
            "expense.write",
            "expense.manager",
            "expense.budget",
            "dashboard",
            "export",
            "audit"));
    role(
        "财务复核",
        "ALL",
        Set.of(
            "expense.read", "expense.finance", "expense.budget", "dashboard", "export", "audit"));
    role("付款登记", "ALL", Set.of("expense.read", "expense.pay", "dashboard", "export", "audit"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.passwordHash = encoder.encode(password);
    a.roleId = db.all(AccessRole.class).getFirst().id;
    a.departmentId = d.id;
    a.enabled = true;
    db.save(a);
    String[][] menus = {
      {"workbench", "我的待办", "My work", "expense.read"},
      {"cases", "费用单据", "Expenses", "expense.read"},
      {"budgets", "部门月预算", "Budgets", "expense.read"},
      {"dashboard", "费用统计", "Overview", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门", "Departments", "admin"},
      {"menus", "导航管理", "Menus", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "费用与付款类别", "Categories", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    for (var e :
        Map.of("timezone", "Asia/Shanghai", "companyName", "知华费用申请与报销", "approvalReminderDays", "7")
            .entrySet()) {
      var s = new SystemSetting();
      s.code = e.getKey();
      s.value = e.getValue();
      db.save(s);
    }
    String[][] categories = {
      {"category", "TRAVEL", "差旅", "Travel"},
      {"category", "TRANSPORT", "交通", "Transport"},
      {"category", "HOSPITALITY", "招待", "Hospitality"},
      {"category", "OFFICE", "办公", "Office"},
      {"payment", "BANK", "银行转账登记", "Bank transfer record"},
      {"payment", "CASH", "现金付款登记", "Cash payment record"}
    };
    for (var c : categories) {
      var x = new DictionaryEntry();
      x.type = c[0];
      x.code = c[1];
      x.name = c[2];
      x.nameEn = c[3];
      db.save(x);
    }
  }

  private void role(String name, String scope, Set<String> permissions) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(permissions);
    db.save(r);
  }
}
