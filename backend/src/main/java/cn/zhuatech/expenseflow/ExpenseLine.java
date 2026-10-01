// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.expenseflow;

import jakarta.persistence.*;
import java.time.*;

/** 报销明细与凭证引用；活动凭证摘要唯一，避免跨单重复申报。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "expense_line")
public class ExpenseLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "case_id")
  public Long caseId;

  @Column(name = "expense_date")
  public LocalDate expenseDate;

  @Column(name = "category")
  public String category;

  @Column(name = "description")
  public String description;

  @Column(name = "amount_cents")
  public long amountCents;

  @Column(name = "issuer")
  public String issuer;

  @Column(name = "reference")
  public String reference;

  @Column(name = "active_receipt_key")
  public String activeReceiptKey;
}
