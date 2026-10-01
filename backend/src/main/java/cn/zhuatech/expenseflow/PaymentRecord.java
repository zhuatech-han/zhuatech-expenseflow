// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.expenseflow;

import jakarta.persistence.*;
import java.time.*;

/** 人工付款及冲销登记；实际到账由企业外部核对，原记录保留。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "expense_payment")
public class PaymentRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "case_id")
  public Long caseId;

  @Column(name = "amount_cents")
  public long amountCents;

  @Column(name = "reference")
  public String reference;

  @Column(name = "method")
  public String method;

  @Column(name = "actor")
  public String actor;

  @Column(name = "payment_date")
  public LocalDate paymentDate;

  @Column(name = "created_at")
  public Instant createdAt;

  @Column(name = "reversed_at")
  public Instant reversedAt;

  @Column(name = "reversal_reason")
  public String reversalReason;

  @Column(name = "reversed_by")
  public String reversedBy;
}
