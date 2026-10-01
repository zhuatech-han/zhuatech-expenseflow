// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.expenseflow;

import jakarta.persistence.*;
import java.time.*;

/** 本人费用申请或报销单；流转冻结、预算和付款均受事务保护。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "expense_case")
public class ExpenseCase {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version
  @Column(name = "version")
  public Long version;

  @Column(name = "change_count")
  public long changeCount;

  @Column(name = "number")
  public String number;

  @Column(name = "kind")
  public String kind;

  @Column(name = "title")
  public String title;

  @Column(name = "category")
  public String category;

  @Column(name = "budget_month")
  public String budgetMonth;

  @Column(name = "department_id")
  public Long departmentId;

  @Column(name = "applicant_id")
  public Long applicantId;

  @Column(name = "manager_id")
  public Long managerId;

  @Column(name = "finance_id")
  public Long financeId;

  @Column(name = "application_id")
  public Long applicationId;

  @Column(name = "status")
  public String status;

  @Column(name = "amount_cents")
  public long amountCents;

  @Column(name = "paid_cents")
  public long paidCents;

  @Column(name = "reserved_cents")
  public long reservedCents;

  @Column(name = "submitted")
  public boolean submitted;

  @Column(name = "purpose")
  public String purpose;

  @Column(name = "created_at")
  public Instant createdAt;

  @Column(name = "updated_at")
  public Instant updatedAt;
}
