// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.expenseflow;

import jakarta.persistence.*;
import java.time.*;

/** 费用命令按单据、操作者和请求键去重，拒绝同键不同内容。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "expense_command")
public class CommandStamp {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "case_id")
  public Long caseId;

  @Column(name = "actor")
  public String actor;

  @Column(name = "request_key")
  public String requestKey;

  @Column(name = "fingerprint")
  public String fingerprint;
}
