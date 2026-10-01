// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.expenseflow;

import jakarta.persistence.*;
import java.time.*;

/** 费用流转历史；每次送审保存不可更改的 JSON 内容与凭证摘要快照。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "expense_event")
public class ExpenseEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "case_id")
  public Long caseId;

  @Column(name = "actor")
  public String actor;

  @Column(name = "action")
  public String action;

  @Column(name = "note", columnDefinition = "text")
  public String note;

  @Column(name = "snapshot", columnDefinition = "text")
  public String snapshot;

  @Column(name = "created_at")
  public Instant createdAt;
}
