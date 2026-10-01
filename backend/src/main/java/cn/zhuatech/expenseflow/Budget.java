// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.expenseflow;

import jakarta.persistence.*;
import java.time.*;

/** 部门、费用类别和月份的预算额度、预留与已批准费用。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "expense_budget")
public class Budget {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version
  @Column(name = "version")
  public Long version;

  @Column(name = "department_id")
  public Long departmentId;

  @Column(name = "category")
  public String category;

  @Column(name = "budget_month")
  public String budgetMonth;

  @Column(name = "limit_cents")
  public long limitCents;

  @Column(name = "reserved_cents")
  public long reservedCents;

  @Column(name = "committed_cents")
  public long committedCents;
}
